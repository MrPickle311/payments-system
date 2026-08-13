package com.example.payment.application.saga;

import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_SUCCESS;
import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFIED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import java.util.Collection;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultExtendedState;

/**
 * The interceptor's only job is detection: recognise a completed join and hand it to the dispatcher.
 * Delivery, durability and threading belong to {@link SagaJoinDispatcher} and are tested separately.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParallelSagaJoinInterceptorTest {

    private static final Long PAYMENT = 4242L;

    @Mock
    private SagaJoinDispatcher joinDispatcher;

    @Mock
    private StateMachine<PaymentState, PaymentEvent> rootStateMachine;

    @Mock
    private State<PaymentState, PaymentEvent> currentState;

    private ParallelSagaJoinInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ParallelSagaJoinInterceptor(joinDispatcher);
        DefaultExtendedState extendedState = new DefaultExtendedState();
        extendedState.getVariables().put(PAYMENT_ID, PAYMENT);
        when(rootStateMachine.getExtendedState()).thenReturn(extendedState);
        when(rootStateMachine.getState()).thenReturn(currentState);
    }

    private void activeStates(Collection<PaymentState> ids) {
        when(currentState.getIds()).thenReturn(ids);
    }

    private void fireStateChange() {
        interceptor.postStateChange(null, null, null, rootStateMachine, rootStateMachine);
    }

    @Test
    @DisplayName("dispatches COMPLETE once all PROCESSING regions have passed")
    void dispatchesCompleteWhenProcessingJoins() {
        activeStates(EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED));

        fireStateChange();

        verify(joinDispatcher).dispatch(rootStateMachine, PAYMENT, SagaJoin.PROCESSING_JOIN, COMPLETE);
    }

    @Test
    @DisplayName("dispatches FAIL when a PROCESSING region failed")
    void dispatchesFailWhenRegionFails() {
        activeStates(EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_DETECTED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED));

        fireStateChange();

        verify(joinDispatcher).dispatch(rootStateMachine, PAYMENT, SagaJoin.PROCESSING_JOIN, FAIL);
    }

    @Test
    @DisplayName("stays silent while a region is still running")
    void doesNotDispatchBeforeAllRegionsFinish() {
        activeStates(EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_EVALUATING));

        fireStateChange();

        verifyNoInteractions(joinDispatcher);
    }

    @Test
    @DisplayName("dispatches the settlement join")
    void dispatchesSettlementJoin() {
        activeStates(EnumSet.of(SETTLEMENT, LEDGER_NOTIFIED));

        fireStateChange();

        verify(joinDispatcher).dispatch(rootStateMachine, PAYMENT, SagaJoin.SETTLEMENT_JOIN, SETTLEMENT_SUCCESS);
    }

    @Test
    @DisplayName("fires on every state change, leaving deduplication to the dispatcher's durable claim")
    void dispatchesAgainOnRepeatedNotification() {
        // The old in-heap putIfAbsent guard lived here and was lost on every restore, so it never
        // actually deduplicated across requests or across pods. Detection is now deliberately
        // stateless; the (payment_id, join_key) unique constraint is the real guard.
        activeStates(EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED));

        fireStateChange();
        fireStateChange();

        verify(joinDispatcher, org.mockito.Mockito.times(2))
                .dispatch(rootStateMachine, PAYMENT, SagaJoin.PROCESSING_JOIN, COMPLETE);
    }

    @Test
    @DisplayName("tolerates a machine with no state")
    void toleratesNullState() {
        when(rootStateMachine.getState()).thenReturn(null);

        fireStateChange();

        verifyNoInteractions(joinDispatcher);
    }

    @Test
    @DisplayName("tolerates a state with no ids")
    void toleratesNullIds() {
        activeStates(null);

        fireStateChange();

        verifyNoInteractions(joinDispatcher);
    }

    @Test
    @DisplayName("still dispatches when the payment id is missing, so the dispatcher can report it")
    void dispatchesEvenWithoutPaymentId() {
        when(rootStateMachine.getExtendedState()).thenReturn(new DefaultExtendedState());
        activeStates(EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED));

        fireStateChange();

        // Swallowing this here would hide a payment that cannot be recovered automatically.
        verify(joinDispatcher).dispatch(eq(rootStateMachine), eq(null), any(), eq(COMPLETE));
        verify(joinDispatcher, never()).dispatch(any(), eq(PAYMENT), any(), any());
    }
}

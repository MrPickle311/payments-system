package com.example.payment.infrastructure.statemachine;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultExtendedState;

/**
 * Covers what the previous fire-and-forget dispatch could not report: whether the event was actually
 * claimed, actually accepted, and what happened when it was not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersistentSagaJoinDispatcherTest {

    private static final Long PAYMENT = 77L;
    private static final SagaJoin JOIN = SagaJoin.PROCESSING_JOIN;
    private static final String JOIN_KEY = "SAGA_PROCESSING_JOIN_TRIGGERED";

    @Mock
    private SagaJoinClaimService claimService;

    @Mock
    private SagaPendingEventRepository pendingEventRepository;

    @Mock
    private StateMachine<PaymentState, PaymentEvent> stateMachine;

    @Mock
    private State<PaymentState, PaymentEvent> state;

    private PersistentSagaJoinDispatcher dispatcher;

    /** Runs submitted work on the calling thread so assertions are deterministic. */
    private static final ExecutorService SAME_THREAD = new java.util.concurrent.AbstractExecutorService() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {}

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        dispatcher = new PersistentSagaJoinDispatcher(claimService, pendingEventRepository, SAME_THREAD);
        when(stateMachine.getExtendedState()).thenReturn(new DefaultExtendedState());
        when(stateMachine.getState()).thenReturn(state);
    }

    @Test
    @DisplayName("delivers the event and confirms the claim when the machine accepts it")
    void marksDispatchedOnAcceptance() {
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(true);
        when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                .thenReturn(true);

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        verify(stateMachine).sendEvent(any(org.springframework.messaging.Message.class));
        verify(pendingEventRepository).markDispatched(PAYMENT, JOIN_KEY);
        verify(pendingEventRepository, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("leaves the claim retryable when the machine DENIES the event")
    void leavesRetryableOnDenial() {
        // The single most silent failure in the old code: sendEvent's accept/deny boolean was
        // discarded, so a denied join event vanished with no log, no retry and no record.
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(true);
        when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                .thenReturn(false);

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        verify(pendingEventRepository).markFailed(eq(PAYMENT), eq(JOIN_KEY), contains("denied"));
        verify(pendingEventRepository, never()).markDispatched(anyLong(), anyString());
    }

    @Test
    @DisplayName("does not deliver when the join is already claimed")
    void doesNotDeliverWhenAlreadyClaimed() {
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(false);

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        verify(stateMachine, never()).sendEvent(any(org.springframework.messaging.Message.class));
        verifyNoInteractions(pendingEventRepository);
    }

    @Test
    @DisplayName("backs off silently when another pod wins the claim race")
    void backsOffWhenLosingTheClaimRace() {
        // Two pods restoring the same composite state both detect the join; the unique constraint
        // on (payment_id, join_key) decides the winner and the loser's transaction rolls back.
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        verify(stateMachine, never()).sendEvent(any(org.springframework.messaging.Message.class));
        verify(pendingEventRepository, never()).markDispatched(anyLong(), anyString());
        verify(pendingEventRepository, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("records the failure when delivery throws instead of losing it")
    void recordsUnexpectedFailures() {
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(true);
        when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                .thenThrow(new IllegalStateException("boom"));

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        verify(pendingEventRepository).markFailed(eq(PAYMENT), eq(JOIN_KEY), contains("boom"));
    }

    @Test
    @DisplayName("refuses to dispatch without a payment id rather than failing obscurely later")
    void refusesWithoutPaymentId() {
        dispatcher.dispatch(stateMachine, null, JOIN, COMPLETE);

        verifyNoInteractions(claimService);
        verifyNoInteractions(pendingEventRepository);
    }

    @Test
    @DisplayName("claims before delivering, so a crash mid-delivery still leaves a durable record")
    void claimsBeforeDelivering() {
        // Ordering is the whole point: the claim (and the state persist it is co-transactional with)
        // must be committed before the event goes out, otherwise a crash during delivery leaves
        // nothing for the recovery pollers to find.
        when(claimService.claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(true);
        when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                .thenReturn(true);

        dispatcher.dispatch(stateMachine, PAYMENT, JOIN, COMPLETE);

        var inOrder = org.mockito.Mockito.inOrder(claimService, stateMachine, pendingEventRepository);
        inOrder.verify(claimService).claim(stateMachine, PAYMENT, COMPLETE, JOIN_KEY);
        inOrder.verify(stateMachine).sendEvent(any(org.springframework.messaging.Message.class));
        inOrder.verify(pendingEventRepository).markDispatched(PAYMENT, JOIN_KEY);
    }

    @Test
    @DisplayName("the join key is derived from the composite, so one composite joins once")
    void joinKeyIsPerComposite() {
        assertThat(SagaJoin.PROCESSING_JOIN.joinKey()).isEqualTo(JOIN_KEY);
        assertThat(SagaJoin.SETTLEMENT_JOIN.joinKey()).isNotEqualTo(JOIN_KEY);
    }
}

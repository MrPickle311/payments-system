package com.example.payment.application.saga;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_SUCCESS;
import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_FAILED;
import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATION_SKIPPED;
import static com.example.payment.domain.enums.PaymentState.FEE_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFICATION_FAILED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFIED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATION_SKIPPED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EXCEEDED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_FAILED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_FAILED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_HIT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT_FAILED;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelSagaJoinInterceptor extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

    private static final Set<PaymentState> PROCESSING_REGION_TERMINAL_STATES =
            EnumSet.of(AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);

    private static final Set<PaymentState> PROCESSING_FAILURE_STATES = EnumSet.of(
            AUTH_REJECTED,
            AUTH_FAILED,
            FRAUD_DETECTED,
            FRAUD_FAILED,
            LIMITS_EXCEEDED,
            LIMITS_FAILED,
            SANCTIONS_HIT,
            SANCTIONS_FAILED,
            FEE_FAILED);

    private static final int PROCESSING_REGION_COUNT = 5;

    private static final Set<PaymentState> COMPENSATING_REGION_TERMINAL_STATES =
            EnumSet.of(FEE_COMPENSATED, FEE_COMPENSATION_SKIPPED, LIMITS_COMPENSATED, LIMITS_COMPENSATION_SKIPPED);

    private static final int COMPENSATING_REGION_COUNT = 2;

    private static final String PROCESSING_JOIN_KEY = "SAGA_PROCESSING_JOIN_TRIGGERED";
    private static final String SETTLEMENT_JOIN_KEY = "SAGA_SETTLEMENT_JOIN_TRIGGERED";
    private static final String COMPENSATING_JOIN_KEY = "SAGA_COMPENSATING_JOIN_TRIGGERED";

    private final ExecutorService virtualThreadExecutor;

    @Override
    public void postStateChange(
            State<PaymentState, PaymentEvent> state,
            Message<PaymentEvent> message,
            Transition<PaymentState, PaymentEvent> transition,
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

        checkStateTransitions(rootStateMachine);
    }

    private void checkStateTransitions(StateMachine<PaymentState, PaymentEvent> rootStateMachine) {
        State<PaymentState, PaymentEvent> currentState = rootStateMachine.getState();
        if (currentState == null) {
            return;
        }

        Collection<PaymentState> ids = currentState.getIds();
        if (ids == null) {
            return;
        }

        if (ids.contains(PROCESSING)) {
            checkProcessingJoin(rootStateMachine, ids);
            return;
        }

        if (ids.contains(SETTLEMENT)) {
            checkSettlementCompletion(rootStateMachine, ids);
            return;
        }

        if (ids.contains(COMPENSATING)) {
            checkCompensatingJoin(rootStateMachine, ids);
        }
    }

    private void checkProcessingJoin(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine, Collection<PaymentState> ids) {
        long finishedCount = ids.stream()
                .filter(s -> PROCESSING_REGION_TERMINAL_STATES.contains(s) || PROCESSING_FAILURE_STATES.contains(s))
                .count();

        if (finishedCount >= PROCESSING_REGION_COUNT) {
            boolean isSet = setVariableInExtendedState(PROCESSING_JOIN_KEY, rootStateMachine);
            if (isSet) {
                boolean anyFailed = ids.stream().anyMatch(PROCESSING_FAILURE_STATES::contains);
                PaymentEvent event = anyFailed ? FAIL : COMPLETE;
                sendEvent(rootStateMachine, event);
            }
        }
    }

    private void checkSettlementCompletion(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine, Collection<PaymentState> ids) {
        if (ids.contains(LEDGER_NOTIFIED)) {
            boolean isSet = setVariableInExtendedState(SETTLEMENT_JOIN_KEY, rootStateMachine);
            if (isSet) {
                sendEvent(rootStateMachine, SETTLEMENT_SUCCESS);
            }
            return;
        }

        if (ids.contains(SETTLEMENT_FAILED) || ids.contains(LEDGER_NOTIFICATION_FAILED)) {
            boolean isSet = setVariableInExtendedState(SETTLEMENT_JOIN_KEY, rootStateMachine);
            if (isSet) {
                sendEvent(rootStateMachine, SETTLEMENT_FAIL);
            }
        }
    }

    private void checkCompensatingJoin(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine, Collection<PaymentState> ids) {
        long finishedCount = ids.stream()
                .filter(COMPENSATING_REGION_TERMINAL_STATES::contains)
                .count();

        if (finishedCount >= COMPENSATING_REGION_COUNT) {
            boolean isSet = setVariableInExtendedState(COMPENSATING_JOIN_KEY, rootStateMachine);
            if (isSet) {
                sendEvent(rootStateMachine, FAIL);
            }
        }
    }

    private static boolean setVariableInExtendedState(
            String key, StateMachine<PaymentState, PaymentEvent> rootStateMachine) {
        var result = rootStateMachine.getExtendedState().getVariables().putIfAbsent(key, Boolean.TRUE);
        return result == null;
    }

    private void sendEvent(StateMachine<PaymentState, PaymentEvent> rootStateMachine, PaymentEvent event) {
        Long paymentId = SagaContextProxy.of(rootStateMachine).getPaymentId();
        log.info("[JoinInterceptor] event={} for paymentId={}", event, paymentId);
        CompletableFuture.runAsync(
                () -> SagaContextProxy.sendEventWithRetries(rootStateMachine, event, paymentId), virtualThreadExecutor);
    }
}

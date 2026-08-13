package com.example.payment.application.saga;

import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_FAILED;
import static com.example.payment.domain.enums.PaymentState.AUTH_PENDING;
import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.FEE_CALCULATED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FEE_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FRAUD_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EXCEEDED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_FAILED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_FAILED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_HIT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.region.RegionExecutionPolicy;

/**
 * Builds a real, running {@code PROCESSING} state machine — the same 5 orthogonal regions and
 * events as {@code statemachine.puml} — without a Spring context, for tests that need real
 * framework behavior rather than a mocked {@link StateMachine}.
 */
public final class ProcessingStateMachineTestSupport {

    private ProcessingStateMachineTestSupport() {}

    public static StateMachine<PaymentState, PaymentEvent> buildStarted(Long paymentId) throws Exception {
        StateMachineBuilder.Builder<PaymentState, PaymentEvent> builder = StateMachineBuilder.builder();

        builder.configureConfiguration()
                .withConfiguration()
                .regionExecutionPolicy(RegionExecutionPolicy.PARALLEL)
                .autoStartup(false);

        builder.configureStates()
                .withStates()
                .initial(PROCESSING)
                .state(PROCESSING)
                .state(SETTLEMENT)
                .state(COMPENSATING)
                .and()
                .withStates()
                .parent(PROCESSING)
                .initial(AUTH_PENDING)
                .state(AUTH_APPROVED)
                .state(AUTH_REJECTED)
                .state(AUTH_FAILED)
                .and()
                .withStates()
                .parent(PROCESSING)
                .initial(FRAUD_EVALUATING)
                .state(FRAUD_PASSED)
                .state(FRAUD_DETECTED)
                .state(FRAUD_FAILED)
                .and()
                .withStates()
                .parent(PROCESSING)
                .initial(LIMITS_EVALUATING)
                .state(LIMITS_OK)
                .state(LIMITS_EXCEEDED)
                .state(LIMITS_FAILED)
                .and()
                .withStates()
                .parent(PROCESSING)
                .initial(SANCTIONS_EVALUATING)
                .state(SANCTIONS_CLEARED)
                .state(SANCTIONS_HIT)
                .state(SANCTIONS_FAILED)
                .and()
                .withStates()
                .parent(PROCESSING)
                .initial(FEE_EVALUATING)
                .state(FEE_CALCULATED)
                .state(FEE_CHARGED)
                .state(FEE_FAILED);

        builder.configureTransitions()
                .withExternal()
                .source(AUTH_PENDING)
                .target(AUTH_APPROVED)
                .event(PaymentEvent.AUTH_SUCCESS)
                .and()
                .withExternal()
                .source(AUTH_PENDING)
                .target(AUTH_REJECTED)
                .event(PaymentEvent.AUTH_REJECT)
                .and()
                .withExternal()
                .source(AUTH_PENDING)
                .target(AUTH_FAILED)
                .event(PaymentEvent.AUTH_FAIL)
                .and()
                .withExternal()
                .source(FRAUD_EVALUATING)
                .target(FRAUD_PASSED)
                .event(PaymentEvent.FRAUD_CLEAR)
                .and()
                .withExternal()
                .source(FRAUD_EVALUATING)
                .target(FRAUD_DETECTED)
                .event(PaymentEvent.FRAUD_ALERT)
                .and()
                .withExternal()
                .source(FRAUD_EVALUATING)
                .target(FRAUD_FAILED)
                .event(PaymentEvent.FRAUD_FAIL)
                .and()
                .withExternal()
                .source(LIMITS_EVALUATING)
                .target(LIMITS_OK)
                .event(PaymentEvent.LIMITS_CLEAR)
                .and()
                .withExternal()
                .source(LIMITS_EVALUATING)
                .target(LIMITS_EXCEEDED)
                .event(PaymentEvent.LIMITS_REJECT)
                .and()
                .withExternal()
                .source(LIMITS_EVALUATING)
                .target(LIMITS_FAILED)
                .event(PaymentEvent.LIMITS_FAIL)
                .and()
                .withExternal()
                .source(SANCTIONS_EVALUATING)
                .target(SANCTIONS_CLEARED)
                .event(PaymentEvent.SANCTIONS_PASS)
                .and()
                .withExternal()
                .source(SANCTIONS_EVALUATING)
                .target(SANCTIONS_HIT)
                .event(PaymentEvent.SANCTIONS_HIT)
                .and()
                .withExternal()
                .source(SANCTIONS_EVALUATING)
                .target(SANCTIONS_FAILED)
                .event(PaymentEvent.SANCTIONS_FAIL)
                .and()
                .withExternal()
                .source(FEE_EVALUATING)
                .target(FEE_CALCULATED)
                .event(PaymentEvent.FEE_CALC_SUCCESS)
                .and()
                .withExternal()
                .source(FEE_CALCULATED)
                .target(FEE_CHARGED)
                .event(PaymentEvent.FEE_CHARGE_SUCCESS)
                .and()
                .withExternal()
                .source(FEE_EVALUATING)
                .target(FEE_FAILED)
                .event(PaymentEvent.FEE_CALC_FAIL)
                .and()
                .withExternal()
                .source(FEE_CALCULATED)
                .target(FEE_FAILED)
                .event(PaymentEvent.FEE_CHARGE_FAIL)
                .and()
                .withExternal()
                .source(PROCESSING)
                .target(SETTLEMENT)
                .event(PaymentEvent.COMPLETE)
                .and()
                .withExternal()
                .source(PROCESSING)
                .target(COMPENSATING)
                .event(PaymentEvent.FAIL);

        StateMachine<PaymentState, PaymentEvent> sm = builder.build();
        sm.getExtendedState().getVariables().put(PAYMENT_ID, paymentId);
        sm.startReactively().block();
        return sm;
    }

    /** Sends the four single-hop success events; caller sends the two-hop FeeCheck events separately. */
    public static void completeAuthFraudLimitsSanctions(StateMachine<PaymentState, PaymentEvent> sm, Long paymentId) {
        send(sm, paymentId, PaymentEvent.AUTH_SUCCESS);
        send(sm, paymentId, PaymentEvent.FRAUD_CLEAR);
        send(sm, paymentId, PaymentEvent.LIMITS_CLEAR);
        send(sm, paymentId, PaymentEvent.SANCTIONS_PASS);
    }

    public static void completeFeeCheck(StateMachine<PaymentState, PaymentEvent> sm, Long paymentId) {
        send(sm, paymentId, PaymentEvent.FEE_CALC_SUCCESS);
        send(sm, paymentId, PaymentEvent.FEE_CHARGE_SUCCESS);
    }

    public static boolean send(StateMachine<PaymentState, PaymentEvent> sm, Long paymentId, PaymentEvent event) {
        return SagaContextProxy.sendEventAndReportAcceptance(sm, event, paymentId);
    }
}

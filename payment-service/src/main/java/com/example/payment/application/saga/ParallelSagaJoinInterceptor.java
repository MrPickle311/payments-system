package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import com.example.payment.domain.saga.SagaJoin.JoinDecision;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

/**
 * Detects parallel-region joins that {@code statemachine.puml} cannot express — it declares
 * {@code PROCESSING --> SETTLEMENT : COMPLETE} as a plain event transition, with nothing to say
 * "only once all five regions have finished".
 *
 * <p>Detection only: the decision is a pure function of the active state set ({@link SagaJoin}),
 * and {@link SagaJoinDispatcher} owns claiming the join durably and delivering the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelSagaJoinInterceptor extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

    private final SagaJoinDispatcher joinDispatcher;

    @Override
    public void postStateChange(
            State<PaymentState, PaymentEvent> state,
            Message<PaymentEvent> message,
            Transition<PaymentState, PaymentEvent> transition,
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

        checkForCompletedJoin(rootStateMachine);
    }

    private void checkForCompletedJoin(StateMachine<PaymentState, PaymentEvent> rootStateMachine) {
        State<PaymentState, PaymentEvent> currentState = rootStateMachine.getState();
        if (currentState == null) {
            return;
        }

        Collection<PaymentState> activeStates = currentState.getIds();
        if (activeStates == null) {
            return;
        }

        Optional<JoinDecision> decision = SagaJoin.decide(activeStates);
        if (decision.isEmpty()) {
            return;
        }

        SagaJoin join = decision.get().join();
        PaymentEvent event = decision.get().event();
        Long paymentId = SagaContextProxy.of(rootStateMachine).getPaymentId();

        log.info(
                "[JoinInterceptor] {} complete for payment {} in states {} - dispatching {}",
                join,
                paymentId,
                activeStates,
                event);

        joinDispatcher.dispatch(rootStateMachine, paymentId, join, event);
    }
}

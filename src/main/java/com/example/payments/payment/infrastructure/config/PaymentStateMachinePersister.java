package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/**
 * Custom {@link StateMachinePersister} that maps between a {@link Payment}
 * entity (the canonical state store) and the in-memory state machine.
 */
@Component
public class PaymentStateMachinePersister
        implements StateMachinePersister<PaymentState, PaymentEvent, Payment> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentStateMachinePersister.class);

    @Override
    public void persist(StateMachine<PaymentState, PaymentEvent> stateMachine,
                        Payment payment) {
        PaymentState newState = stateMachine.getState().getId();
        log.debug("[Persister] Persisting state {} for payment {}", newState, payment.getId());
        payment.setState(newState.name());
    }

    @Override
    public StateMachine<PaymentState, PaymentEvent> restore(
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            Payment payment) {

        PaymentState storedState = payment.currentState();
        log.debug("[Persister] Restoring state machine to state {} for payment {}",
                storedState, payment.getId());

        DefaultExtendedState extendedState = new DefaultExtendedState();
        extendedState.getVariables().put("paymentId",       payment.getId());
        extendedState.getVariables().put("paymentAmount",   payment.getMoney().getAmount());
        extendedState.getVariables().put("paymentCurrency", payment.getMoney().getCurrency());
        extendedState.getVariables().put("paymentCreatedAt", payment.getCreatedAt());
        extendedState.getVariables().put("isRestoring",     Boolean.TRUE);

        DefaultStateMachineContext<PaymentState, PaymentEvent> context =
                new DefaultStateMachineContext<>(storedState, null, null, extendedState);

        stateMachine.stop();

        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(context));

        stateMachine.start();

        stateMachine.getExtendedState().getVariables().put("isRestoring", Boolean.FALSE);

        return stateMachine;
    }
}

package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.Payment;
import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
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
        String stateStr = stateMachine.getState().getIds().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(","));
        log.debug("[Persister] Persisting state {} for payment {}", stateStr, payment.getId());
        payment.setState(stateStr);
    }

    @Override
    public StateMachine<PaymentState, PaymentEvent> restore(
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            Payment payment) {

        String[] stateNames = payment.getState().split(",");
        PaymentState storedState = PaymentState.valueOf(stateNames[0]);
        log.debug("[Persister] Restoring state machine to state {} for payment {}",
                payment.getState(), payment.getId());

        DefaultExtendedState extendedState = new DefaultExtendedState();
        extendedState.getVariables().put("paymentId",       payment.getId());
        extendedState.getVariables().put("paymentAmount",   payment.getMoney().getAmount());
        extendedState.getVariables().put("paymentCurrency", payment.getMoney().getCurrency());
        extendedState.getVariables().put("paymentCreatedAt", payment.getCreatedAt());
        extendedState.getVariables().put("isRestoring",     Boolean.TRUE);

        org.springframework.statemachine.StateMachineContext<PaymentState, PaymentEvent> context;
        if (stateNames.length > 1) {
            java.util.List<org.springframework.statemachine.StateMachineContext<PaymentState, PaymentEvent>> childs = new java.util.ArrayList<>();
            for (int i = 1; i < stateNames.length; i++) {
                childs.add(new DefaultStateMachineContext<>(PaymentState.valueOf(stateNames[i]), null, null, null));
            }
            context = new DefaultStateMachineContext<>(childs, storedState, null, null, extendedState);
        } else {
            context = new DefaultStateMachineContext<>(storedState, null, null, extendedState);
        }

        stateMachine.stop();

        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(context));

        stateMachine.start();

        stateMachine.getExtendedState().getVariables().put("isRestoring", Boolean.FALSE);

        return stateMachine;
    }
}

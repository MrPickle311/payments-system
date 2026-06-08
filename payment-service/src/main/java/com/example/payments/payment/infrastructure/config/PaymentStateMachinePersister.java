package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;
import static com.example.payments.payment.domain.PaymentConstants.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class PaymentStateMachinePersister
    implements StateMachinePersister<PaymentState, PaymentEvent, Payment> {



  @Override
  public void persist(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
    String stateStr = stateMachine.getState().getIds().stream()

        .map(Enum::name)

        .collect(Collectors.joining(","));
    log.debug("[Persister] Persisting state {} for payment {}", stateStr, payment.getId());
    payment.setState(stateStr);
  }

  @Override
  public StateMachine<PaymentState, PaymentEvent> restore(
      StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {

    String[] stateNames = payment.getState().split(",");
    PaymentState storedState = PaymentState.valueOf(stateNames[0]);
    log.debug("[Persister] Restoring state machine to state {} for payment {}", payment.getState(),
        payment.getId());

    DefaultExtendedState extendedState = new DefaultExtendedState();
    extendedState.getVariables().put(PAYMENT_ID, payment.getId());
    extendedState.getVariables().put(PAYMENT_AMOUNT, payment.getMoney().amount());
    extendedState.getVariables().put(PAYMENT_CURRENCY, payment.getMoney().currency());
    extendedState.getVariables().put(PAYMENT_CREATED_AT, payment.getCreatedAt());
    extendedState.getVariables().put(IS_RESTORING, Boolean.TRUE);

    StateMachineContext<PaymentState, PaymentEvent> context;
    if (stateNames.length > 1) {
      List<StateMachineContext<PaymentState, PaymentEvent>> childs =
          Arrays.stream(stateNames).skip(1)

              .map(name -> new DefaultStateMachineContext<PaymentState, PaymentEvent>(
                  PaymentState.valueOf(name), null, null, null))

              .collect(Collectors.toList());
      context = new DefaultStateMachineContext<>(childs, storedState, null, null, extendedState);
    } else {
      context = new DefaultStateMachineContext<>(storedState, null, null, extendedState);
    }

    stateMachine.stop();

    stateMachine.getStateMachineAccessor()
        .doWithAllRegions(access -> access.resetStateMachine(context));

    stateMachine.start();

    stateMachine.getExtendedState().getVariables().put(IS_RESTORING, Boolean.FALSE);

    return stateMachine;
  }
}

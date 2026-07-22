package com.example.payment.infrastructure.config;

import static com.example.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.PaymentConstants.PROCESSING_FEE;

import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.Payment;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentStateMachinePersister
    implements StateMachinePersister<PaymentState, PaymentEvent, Payment> {

  private static final String COMMA = ",";

  @Override
  public void persist(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
    String stateStr = stateMachine.getState().getIds().stream().map(Enum::name)
        .collect(Collectors.joining(COMMA));
    log.debug("[Persister] Persisting state {} for payment {}", stateStr, payment.getId());
    payment.setState(stateStr);
    var proxy = SagaContextProxy.of(stateMachine);
    payment.markFraudEvaluation(proxy.getFraudScore(), proxy.getFraudRisk());
    payment.updateFinancialDetails(proxy.getProcessingFee(), proxy.getNetAmount());
  }

  @Override
  public StateMachine<PaymentState, PaymentEvent> restore(
      StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
    String[] stateNames = payment.getState().split(COMMA);
    log.debug("[Persister] Restoring state machine to state {} for payment {}", payment.getState(),
        payment.getId());
    StateMachineContext<PaymentState, PaymentEvent> ctx = createContext(stateNames,
        PaymentState.valueOf(stateNames[0]), createExtendedState(payment));
    stateMachine.stop();
    stateMachine.getStateMachineAccessor().doWithRegion(access -> access.resetStateMachine(ctx));
    stateMachine.start();
    SagaContextProxy.of(stateMachine).setIsRestoring(Boolean.FALSE);
    return stateMachine;
  }

  private DefaultExtendedState createExtendedState(Payment payment) {
    DefaultExtendedState extendedState = new DefaultExtendedState();
    extendedState.getVariables().put(PAYMENT_ID, payment.getId());
    extendedState.getVariables().put(PAYMENT_AMOUNT, payment.getMoney().amount());
    extendedState.getVariables().put(PAYMENT_CURRENCY, payment.getMoney().currency());
    if (payment.getCreatedAt() != null) {
      extendedState.getVariables().put(PAYMENT_CREATED_AT, payment.getCreatedAt());
    }
    extendedState.getVariables().put(IS_RESTORING, Boolean.TRUE);
    putSagaVariables(extendedState, payment);
    return extendedState;
  }

  private void putSagaVariables(DefaultExtendedState extendedState, Payment payment) {
    if (payment.getFraudScore() != null) {
      extendedState.getVariables().put(FRAUD_SCORE, payment.getFraudScore());
    }
    if (payment.getFraudRisk() != null) {
      extendedState.getVariables().put(FRAUD_RISK, payment.getFraudRisk());
    }
    if (payment.getProcessingFee() != null) {
      extendedState.getVariables().put(PROCESSING_FEE, payment.getProcessingFee());
    }
    if (payment.getNetAmount() != null) {
      extendedState.getVariables().put(NET_AMOUNT, payment.getNetAmount());
    }
  }

  private StateMachineContext<PaymentState, PaymentEvent> createContext(String[] stateNames,
      PaymentState storedState, DefaultExtendedState extendedState) {
    if (stateNames.length > 1) {
      List<StateMachineContext<PaymentState, PaymentEvent>> childs = Arrays.stream(stateNames)
          .skip(1).map(name -> new DefaultStateMachineContext<PaymentState, PaymentEvent>(
              PaymentState.valueOf(name), null, null, null))
          .collect(Collectors.toList());
      return new DefaultStateMachineContext<>(childs, storedState, null, null, extendedState);
    }
    return new DefaultStateMachineContext<>(storedState, null, null, extendedState);
  }
}

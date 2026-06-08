package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Component
@Slf4j
public class PaymentStateMachineInterceptor
    extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {



  private final PaymentHistoryRepository paymentHistoryRepository;

  public PaymentStateMachineInterceptor(PaymentHistoryRepository paymentHistoryRepository) {
    this.paymentHistoryRepository = paymentHistoryRepository;
  }

  @Override
  public void preStateChange(State<PaymentState, PaymentEvent> state, Message<PaymentEvent> message,
      Transition<PaymentState, PaymentEvent> transition,
      StateMachine<PaymentState, PaymentEvent> stateMachine,
      StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

    if (transition != null && transition.getSource() != null) {
      PaymentState source = transition.getSource().getId();
      PaymentState target = transition.getTarget().getId();
      PaymentEvent event =
          transition.getTrigger() != null ? transition.getTrigger().getEvent() : null;

      Long paymentId = stateMachine.getExtendedState().get("paymentId", Long.class);

      if (Boolean.TRUE.equals(stateMachine.getExtendedState().get("isRestoring", Boolean.class))) {
        log.debug("[Interceptor] Skipping history for restoration: payment={} {} -> {}", paymentId,
            source, target);
        return;
      }

      log.info("[Interceptor] Record transition for payment {}: {} --({})--> {}", paymentId, source,
          event, target);

      paymentHistoryRepository.save(PaymentHistory.builder().paymentId(paymentId)
          .fromState(source.name()).toState(target.name())
          .event(event != null ? event.name() : "AUTO").timestamp(LocalDateTime.now()).build());
    }
  }
}

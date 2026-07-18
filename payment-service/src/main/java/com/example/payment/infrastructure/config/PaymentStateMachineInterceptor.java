package com.example.payment.infrastructure.config;

import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.time.LocalDateTime;
import java.time.ZoneId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentStateMachineInterceptor
    extends StateMachineListenerAdapter<PaymentState, PaymentEvent> {

  private static final String PAYMENT_ID = "paymentId";
  private static final String IS_RESTORING = "isRestoring";

  private final PaymentHistoryRepository paymentHistoryRepository;

  public PaymentStateMachineInterceptor(PaymentHistoryRepository paymentHistoryRepository) {
    this.paymentHistoryRepository = paymentHistoryRepository;
  }

  @Override
  public void stateContext(StateContext<PaymentState, PaymentEvent> stateContext) {
    if (stateContext.getStage() == StateContext.Stage.STATE_CHANGED) {
      processStateChange(stateContext);
    }
  }

  private void processStateChange(StateContext<PaymentState, PaymentEvent> context) {
    if (context.getSource() == null || context.getTarget() == null) {
      return;
    }
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class))) {
      log.debug("[Interceptor] Skipping restoration: payment={}", paymentId);
      return;
    }
    savePaymentHistory(context, paymentId);
  }

  private void savePaymentHistory(StateContext<PaymentState, PaymentEvent> context,
      Long paymentId) {
    PaymentState source = context.getSource().getId();
    PaymentState target = context.getTarget().getId();
    PaymentEvent event = context.getEvent();
    log.info("[Interceptor] Record transition for payment {}: {} --({})--> {}", paymentId, source,
        event, target);
    paymentHistoryRepository
        .save(PaymentHistory.builder().paymentId(paymentId).fromState(source.name())
            .toState(target.name()).event(event != null ? event.name() : "AUTO")
            .timestamp(LocalDateTime.now(ZoneId.systemDefault())).build());
  }
}

package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import static com.example.payments.payment.domain.PaymentConstants.REJECT_REASON;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentStateMachineInterceptor
    extends StateMachineListenerAdapter<PaymentState, PaymentEvent> {

  private static final String PAYMENT_ID = "paymentId";
  private static final String IS_RESTORING = "isRestoring";
  private static final String MANUAL_REVIEW_EVENT_TYPE = "PaymentManualReviewEvent";
  private static final String TYPE_HEADER = "type";

  private final PaymentHistoryRepository paymentHistoryRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

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
      return;
    }
    updateRejectReasonIfTerminal(context);
    savePaymentHistory(context, paymentId);
  }

  private void updateRejectReasonIfTerminal(StateContext<PaymentState, PaymentEvent> context) {
    PaymentState target = context.getTarget().getId();
    if (target == PaymentState.REJECTED || target == PaymentState.FAILED) {
      String reason = context.getExtendedState().get(REJECT_REASON, String.class);
      if (reason == null && context.getEvent() != null) {
        context.getExtendedState().getVariables().put(REJECT_REASON, context.getEvent().name());
      }
    }
  }

  private void savePaymentHistory(StateContext<PaymentState, PaymentEvent> context,
      Long paymentId) {
    PaymentState source = context.getSource().getId();
    PaymentState target = context.getTarget().getId();
    PaymentEvent event = context.getEvent();
    log.info("[Interceptor] Record transition for payment {}: {} --({})--> {}", paymentId, source,
        event, target);
    paymentHistoryRepository.save(PaymentHistory.builder().paymentId(paymentId)
        .fromState(source.name()).toState(target.name())
        .event(event != null ? event.name() : "AUTO").timestamp(LocalDateTime.now()).build());
    if (target == PaymentState.MANUAL_REVIEW) {
      publishManualReviewEvent(paymentId);
    }
    if (target == PaymentState.COMPLETED) {
      publishCompletedEvent(paymentId);
    }
  }

  private void publishCompletedEvent(Long paymentId) {
    if (kafkaTemplate == null || objectMapper == null) {
      return;
    }
    try {
      String json = objectMapper
          .writeValueAsString(Map.of(PAYMENT_ID, paymentId, TYPE_HEADER, "PaymentCompletedEvent"));
      ProducerRecord<String, String> rec = new ProducerRecord<>("payment-events", json);
      rec.headers().add(TYPE_HEADER, "PaymentCompletedEvent".getBytes(StandardCharsets.UTF_8));
      kafkaTemplate.send(rec);
    } catch (Exception e) {
      log.error("[Interceptor] Failed to publish completed: {}", e.getMessage());
    }
  }

  private void publishManualReviewEvent(Long paymentId) {
    if (kafkaTemplate == null || objectMapper == null) {
      return;
    }
    try {
      String json = objectMapper
          .writeValueAsString(Map.of(PAYMENT_ID, paymentId, TYPE_HEADER, MANUAL_REVIEW_EVENT_TYPE));
      ProducerRecord<String, String> rec = new ProducerRecord<>("payment-events", json);
      rec.headers().add(TYPE_HEADER, MANUAL_REVIEW_EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
      kafkaTemplate.send(rec);
    } catch (Exception e) {
      log.error("[Interceptor] Failed to publish: {}", e.getMessage());
    }
  }
}

package com.example.payments.payment.api;

import com.example.payments.payment.api.model.ApiWebhookPayload;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.api.generated.WebhookApi;
import com.example.payments.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public final class PaymentWebhookController implements WebhookApi {

  private final PaymentService paymentService;

  @Override
  public ResponseEntity<Void> handlePaymentEvent(final ApiWebhookPayload payload) {
    log.info("[Webhook] Received event={} for paymentId={}", payload.getEvent(),
        payload.getPaymentId());

    final PaymentEvent event = PaymentEvent.valueOf(payload.getEvent());
    paymentService.processEvent(payload.getPaymentId(), event);

    return ResponseEntity.ok().build();
  }
}

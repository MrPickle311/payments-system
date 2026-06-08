package com.example.payments.payment.api;


import com.example.payments.payment.api.model.ApiWebhookPayload;

import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.api.generated.WebhookApi;
import com.example.payments.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public final class PaymentWebhookController implements WebhookApi {


  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(PaymentWebhookController.class);


  private final PaymentService paymentService;


  @Override
  public ResponseEntity<Void> handlePaymentEvent(final ApiWebhookPayload payload) {
    LOG.info("[Webhook] Received event={} for paymentId={}", payload.getEvent(),
        payload.getPaymentId());

    final PaymentEvent event = PaymentEvent.valueOf(payload.getEvent());
    paymentService.processEvent(payload.getPaymentId(), event);

    return ResponseEntity.ok().build();
  }
}

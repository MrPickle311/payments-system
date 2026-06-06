package com.example.payments.payment.api;

import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.payment.api.generated.WebhookApi;
import com.example.payments.payment.application.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for webhook.
 */
@RestController
@RequiredArgsConstructor
public final class PaymentWebhookController implements WebhookApi {

    /** Logger. */
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(PaymentWebhookController.class);

    /** Payment service. */
    private final PaymentService paymentService;

    /**
     * Handle payment event.
     *
     * @param payload the payload
     * @return the response
     */
    @Override
    public ResponseEntity<Void> handlePaymentEvent(
            final com.example.payments.payment.api.model.WebhookPayload
                    payload) {
        LOG.info("[Webhook] Received event={} for paymentId={}",
                payload.getEvent(), payload.getPaymentId());

        final PaymentEvent event = PaymentEvent.valueOf(payload.getEvent());
        paymentService.processEvent(payload.getPaymentId(), event);

        return ResponseEntity.ok().build();
    }
}

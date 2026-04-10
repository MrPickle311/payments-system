package com.example.payments.payment.api;

import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.application.dto.WebhookPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin webhook endpoint that represents the callback from an external payment gateway.
 *
 * <p><b>Responsibility boundary:</b> this controller does exactly three things:
 * <ol>
 *   <li>Receive and deserialise the gateway callback.</li>
 *   <li>Validate the payload structure (Bean Validation).</li>
 *   <li>Delegate to {@link PaymentService#processEvent} and return HTTP 200.</li>
 * </ol>
 *
 * No business logic lives here.  Error handling (unknown payment, illegal
 * transition, etc.) is centralised in
 * {@link com.example.payments.payment.api.GlobalExceptionHandler}.
 *
 * <p>In production you would also verify the gateway's HMAC/signature on the
 * raw request body before deserialising the payload.
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentWebhookController.class);


    private final PaymentService paymentService;

    /**
     * Receives a payment event callback from the gateway.
     *
     * <p>Example payload:
     * <pre>{@code
     * POST /api/webhook/payment
     * {
     *   "paymentId": 42,
     *   "event": "AUTHORIZE"
     * }
     * }</pre>
     */
    @PostMapping("/payment")
    public ResponseEntity<Void> handlePaymentEvent(@RequestBody @Valid WebhookPayload payload) {
        log.info("[Webhook] Received event={} for paymentId={}",
                payload.getEvent(), payload.getPaymentId());

        paymentService.processEvent(payload.getPaymentId(), payload.getEvent());

        return ResponseEntity.ok().build();
    }
}

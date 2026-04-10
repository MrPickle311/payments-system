package com.example.payments.payment.api;

import com.example.payments.fee.application.FeeCalculationService;
import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.application.dto.CreatePaymentRequest;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public REST API for payment management and status queries.
 *
 * <p>Separated from {@link PaymentWebhookController} because the two surfaces
 * have different security, rate-limiting, and authentication requirements in
 * a real deployment (e.g. the webhook endpoint is called by the gateway, not
 * by end-users).
 */
@RestController
@RequestMapping("/api/payments")

public class PaymentController {

    public PaymentController(PaymentService paymentService, FeeCalculationService feeCalculationService) {
        this.paymentService = paymentService;
        this.feeCalculationService = feeCalculationService;
    }

    private final PaymentService paymentService;
    private final FeeCalculationService feeCalculationService;

    /**
     * Creates a new payment record in the {@code NEW} state.
     *
     * <p>Example:
     * <pre>{@code
     * POST /api/payments
     * {
     *   "transactionId": "pi_3NyZ9B2eZvKYlo2C0Vq2E1mG",
     *   "amount": 99.99,
     *   "currency": "USD"
     * }
     * }</pre>
     */
    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody @Valid CreatePaymentRequest request) {
        Payment created = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Returns the current state of a payment. */
    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * Returns the fee breakdown persisted when the payment was captured (COMPLETE event).
     * Only available after the payment reaches COMPLETED state.
     */
    @GetMapping("/{id}/fee")
    public ResponseEntity<PaymentFee> getPaymentFee(@PathVariable Long id) {
        return ResponseEntity.ok(feeCalculationService.getFee(id));
    }

    /**
     * Returns the complete, chronologically ordered audit trail for a payment.
     *
     * <p>Each entry records the source state, target state, and triggering event
     * so the full lifecycle can be reconstructed at any point in time.
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentHistory>> getPaymentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(id));
    }
}

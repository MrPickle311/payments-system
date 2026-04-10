package com.example.payments.payment.application.dto;

import com.example.payments.payment.domain.enums.PaymentEvent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload received from the external payment gateway webhook.
 *
 * Kept intentionally minimal: the webhook controller reads only what it needs
 * to route to the service and ignores any additional gateway-specific fields.
 */
@Data
public class WebhookPayload {

    @NotNull(message = "paymentId is required")
    private Long paymentId;

    @NotNull(message = "event is required")
    private PaymentEvent event;

}


package com.example.payment.application.compensation;

import com.example.payment.domain.gateway.LimitsGateway;
import com.example.payment.domain.gateway.WalletDebitCommand;
import com.example.payment.domain.gateway.WalletGateway;
import com.example.payment.domain.PaymentConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationConsumer {

    private static final String EVENT_TYPE_FEE_REFUND = "FEE_REFUND_COMPENSATION";
    private static final String EVENT_TYPE_LIMITS_RELEASE = "LIMITS_RELEASE_COMPENSATION";

    private static final String SUFFIX_FEE_REFUND = "-FEE_REFUND";
    private static final String SUFFIX_LIMITS_RELEASE = "-LIMITS_RELEASE";

    private final WalletGateway walletGateway;
    private final LimitsGateway limitsGateway;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${compensation.topic:payment-compensations}",
            groupId = "${compensation.consumer.group-id:payment-service-compensation}")
    public void consume(@Payload String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            JsonNode data = envelope.path("data");

            log.info("[CompensationConsumer] Received eventType={}", eventType);

            if (EVENT_TYPE_FEE_REFUND.equals(eventType)) {
                handleFeeRefund(data);
            } else if (EVENT_TYPE_LIMITS_RELEASE.equals(eventType)) {
                handleLimitsRelease(data);
            } else {
                log.warn("[CompensationConsumer] Unknown eventType={}, skipping", eventType);
            }
        } catch (Exception ex) {
            log.error("[CompensationConsumer] Failed to process compensation message: {}", ex.getMessage(), ex);
            throw new CompensationProcessingException("Failed to process compensation", ex);
        }
    }

    private void handleFeeRefund(JsonNode data) throws Exception {
        FeeRefundPayload payload = objectMapper.treeToValue(data, FeeRefundPayload.class);
        log.info("[CompensationConsumer] Retrying fee refund for paymentId={} targetUserId={} amount={}",
                payload.getPaymentId(), payload.getTargetUserId(), payload.getAmount());

        var response = walletGateway.debit(WalletDebitCommand.builder()
                .paymentId(payload.getPaymentId())
                .sourceUserId(PaymentConstants.INTERNAL_FEE_USER_ID)
                .targetUserId(payload.getTargetUserId())
                .amount(payload.getAmount())
                .currency(payload.getCurrency())
                .idempotencyKey(payload.getPaymentId() + SUFFIX_FEE_REFUND)
                .build());

        log.info("[CompensationConsumer] Fee refund completed paymentId={} status={}",
                payload.getPaymentId(), response.getStatus());
    }

    private void handleLimitsRelease(JsonNode data) throws Exception {
        LimitsReleasePayload payload = objectMapper.treeToValue(data, LimitsReleasePayload.class);
        log.info("[CompensationConsumer] Retrying limits release for paymentId={} sourceUserId={} amount={}",
                payload.getPaymentId(), payload.getSourceUserId(), payload.getAmount());

        var response = limitsGateway.releaseLimit(
                payload.getPaymentId(),
                payload.getSourceUserId(),
                payload.getAmount(),
                payload.getPaymentId() + SUFFIX_LIMITS_RELEASE);

        if (!response.getReleased()) {
            log.warn("[CompensationConsumer] Limits release returned released=false for paymentId={}",
                    payload.getPaymentId());
        } else {
            log.info("[CompensationConsumer] Limits released successfully for paymentId={}", payload.getPaymentId());
        }
    }
}

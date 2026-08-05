package com.example.payment.application.service;

import com.example.payments.common.sharedkernel.outbox.OutboxEventEntity;
import com.example.payments.common.sharedkernel.outbox.OutboxRepositoryProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final OutboxRepositoryProxy outboxRepositoryProxy;

    public void sendWebhook(Long paymentId, String status) {
        if (paymentId == null) {
            log.error("[WebhookService] Cannot send webhook outbox event with null paymentId");
            return;
        }
        log.debug("[WebhookService] Saving outbox {} webhook for paymentId={}", status, paymentId);
        String payload = toJsonPayload(paymentId, status);
        OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .aggregateId(String.valueOf(paymentId))
                .aggregateType("Payment")
                .eventType("WEBHOOK_EVENT")
                .payload(payload)
                .topic("payment-webhooks")
                .processed(false)
                .build();
        outboxRepositoryProxy.save(outboxEvent);
    }

    private static String toJsonPayload(Long paymentId, String status) {
        return String.format("{\"paymentId\":%d,\"status\":\"%s\"}", paymentId, status);
    }
}

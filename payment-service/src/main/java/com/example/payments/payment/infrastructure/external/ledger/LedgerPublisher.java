package com.example.payments.payment.infrastructure.external.ledger;

import com.example.payments.common.dto.LedgerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Publisher for Ledger events via Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishEvent(Long paymentId, BigDecimal gross, BigDecimal net, String currency) {
        log.info("[PaymentService -> LedgerPublisher] Publishing ledger event to Kafka for paymentId={}", paymentId);

        String topic = "payment-ledger-events";
        String event = String.format("{\"paymentId\":%d,\"grossAmount\":%s,\"netAmount\":%s,\"currency\":\"%s\",\"timestamp\":\"%s\"}",
                paymentId, gross, net, currency, LocalDateTime.now());

        try {
            kafkaTemplate.send(topic, event);
        } catch (Exception e) {
            log.error("[LedgerPublisher] Failed to send Kafka message (no broker available?): {}", e.getMessage());
        }
    }
}

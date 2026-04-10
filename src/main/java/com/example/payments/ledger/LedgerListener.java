package com.example.payments.ledger;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mock Ledger Service (External Service consuming via Kafka).
 */
@Slf4j
@Service
public class LedgerListener {

    @Data
    public static class LedgerEvent {
        private Long paymentId;
        private BigDecimal grossAmount;
        private BigDecimal netAmount;
        private String currency;
        private LocalDateTime timestamp;
    }

    @KafkaListener(topics = "payment-ledger-events", groupId = "ledger-service-group")
    public void consume(String eventJson) {
        log.info("[LedgerService] Consumed ledger event from Kafka: {}", eventJson);
        // In a real system, would use a JSON deserializer and save to ledger DB
    }
}

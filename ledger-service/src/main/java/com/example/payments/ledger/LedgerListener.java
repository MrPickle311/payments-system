package com.example.payments.ledger;

import com.example.payments.common.dto.LedgerEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Mock Ledger Service (External Service consuming via Kafka).
 */
@Slf4j
@Service
public class LedgerListener {

    @KafkaListener(topics = "payment-ledger-events", groupId = "ledger-service-group")
    public void consume(String eventJson) {
        log.info("[LedgerService] Consumed ledger event from Kafka: {}", eventJson);
        // In a real system, would use a JSON deserializer and save to ledger DB
    }
}

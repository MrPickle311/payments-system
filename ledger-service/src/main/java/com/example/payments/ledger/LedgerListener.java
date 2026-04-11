package com.example.payments.ledger;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.application.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerListener {
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-ledger-events", groupId = "ledger-service-group")
    public void consume(String eventJson) {
        log.info("[LedgerService] Consumed ledger event from Kafka: {}", eventJson);
        try {
            LedgerEvent event = objectMapper.readValue(eventJson, LedgerEvent.class);
            ledgerService.record(event);
        } catch (Exception e) {
            log.error("[LedgerService] Failed to deserialize or record ledger event: {}", e.getMessage());
        }
    }
}

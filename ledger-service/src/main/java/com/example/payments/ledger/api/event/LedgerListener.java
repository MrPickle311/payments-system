package com.example.payments.ledger.api.event;

import static com.example.payments.ledger.common.LedgerConstants.GROUP_ID;
import static com.example.payments.ledger.common.LedgerConstants.TOPIC;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.application.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerListener {
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(String eventJson) {
        log.info("[LedgerService] Consumed ledger event from Kafka: {}", eventJson);
        try {
            LedgerEvent event = objectMapper.readValue(eventJson, LedgerEvent.class);
            ledgerService.recordEvent(event);
        } catch (Exception e) {
            log.error("[LedgerService] Failed to deserialize or record ledger event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing ledger event", e);
        }
    }
}

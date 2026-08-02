package com.example.payments.export.listener;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("listener")
@RequiredArgsConstructor
public class LedgerEventListener {

    private final PaymentExportStagingRepository stagingRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${export.topic}",
            containerFactory = "batchKafkaListenerContainerFactory",
            groupId = "export-staging-consumer")
    public void onMessages(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        List<PaymentExportStaging> rows = records.stream()
                .map(r -> parseToStaging(r.value()))
                .filter(Objects::nonNull)
                .toList();
        stagingRepository.saveAll(rows);
        ack.acknowledge();
        log.info("[Listener] Staged and acked {} events", rows.size());
    }

    private PaymentExportStaging parseToStaging(String json) {
        try {
            LedgerEvent ledgerEvent = objectMapper.readValue(json, LedgerEvent.class);
            return PaymentExportStaging.builder()//TODO: move it into mapper
                    .paymentId(ledgerEvent.getPaymentId())
                    .grossAmount(ledgerEvent.getGrossAmount())
                    .netAmount(ledgerEvent.getNetAmount())
                    .currency(ledgerEvent.getCurrency())
                    .eventTimestamp(ledgerEvent.getTimestamp())
                    .status(PaymentExportStaging.ExportStatus.PENDING)
                    .createdAt(ledgerEvent.getTimestamp())
                    .retryCount(0)
                    .build();
        } catch (Exception ex) {
            log.warn("Moving these events: {} to DLT", json ,ex);
            throw new RuntimeException("Failed to parse event: " + json, ex);
        }
    }
}

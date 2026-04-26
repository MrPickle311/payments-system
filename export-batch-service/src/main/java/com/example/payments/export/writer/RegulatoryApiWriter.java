package com.example.payments.export.writer;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.dto.RegulatoryReportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryApiWriter implements ItemWriter<LedgerEvent> {

    private final RestTemplate restTemplate;
    @Value("${export.regulatory.url:http://localhost:8084/api/v1/regulatory/report}")
    private String regulatoryUrl;

    @Override
    public void write(Chunk<? extends LedgerEvent> chunk) throws Exception {
        log.info("[RegulatoryApiWriter] Preparing to export chunk of {} payments", chunk.size());

        List<RegulatoryReportRequest.ExportedPayment> exportedPayments = chunk.getItems().stream()
                .map(event -> RegulatoryReportRequest.ExportedPayment.builder()
                        .paymentId(event.getPaymentId())
                        .grossAmount(event.getGrossAmount())
                        .netAmount(event.getNetAmount())
                        .currency(event.getCurrency())
                        .timestamp(event.getTimestamp())
                        .build())
                .toList();

        String reportId = generateReportId(chunk.getItems());

        RegulatoryReportRequest request = RegulatoryReportRequest.builder()
                .reportId(reportId)
                .payments(exportedPayments)
                .build();

        log.info("[RegulatoryApiWriter] Sending report id={} to {}", request.getReportId(), regulatoryUrl);
        log.info("Number of chunks sent is {} for report id {}", chunk.size(), request.getReportId());
        
        // This call will be retried by Spring Batch if it throws an exception (and configured so)
        restTemplate.postForLocation(regulatoryUrl, request);
        
        log.info("[RegulatoryApiWriter] Report id={} sent successfully", request.getReportId());
    }

    private String generateReportId(List<? extends LedgerEvent> items) {
        // Collect all payment IDs, sort them to ensure same hash, and join
        String ids = items.stream()
                .map(LedgerEvent::getPaymentId)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(ids.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("[Idempotency] Could not generate MD5 hash, falling back to simple hashcode");
            return "fallback-" + ids.hashCode();
        }
    }
}

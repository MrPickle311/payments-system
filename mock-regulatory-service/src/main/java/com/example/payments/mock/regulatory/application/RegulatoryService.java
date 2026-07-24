package com.example.payments.mock.regulatory.application;

import static com.example.payments.mock.regulatory.common.RegulatoryConstants.ACCEPTED_RESPONSE;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.CHAOS_RESPONSE;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.DUPLICATE_RESPONSE;

import com.example.payments.mock.regulatory.application.dto.RegulatoryReportDto;
import com.example.payments.mock.regulatory.config.RegulatoryProperties;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryService {

    private final SecureRandom random = new SecureRandom();
    private final Set<String> seenReportIds = new HashSet<>();
    private final RegulatoryProperties regulatoryProperties;

    public String processReport(RegulatoryReportDto request) {
        if (isDuplicate(request.getReportId())) {
            return handleDuplicate(request.getReportId());
        }
        log.info(
                "[MockRegulatory] Received report: id={}, count={}",
                request.getReportId(),
                request.getPayments().size());
        if (shouldSimulateFailure()) {
            return simulateFailure();
        }
        return processValidReport(request.getReportId());
    }

    private String handleDuplicate(String reportId) {
        log.warn("[MockRegulatory] IDEMPOTENCY HIT: Already processed reportId={}", reportId);
        return DUPLICATE_RESPONSE;
    }

    private String simulateFailure() {
        log.error("[MockRegulatory] CHAOS MODE: Simulating 500");
        throw new IllegalStateException(CHAOS_RESPONSE);
    }

    private String processValidReport(String reportId) {
        seenReportIds.add(reportId);
        log.info("[MockRegulatory] Report processed successfully");
        return ACCEPTED_RESPONSE;
    }

    private boolean isDuplicate(String reportId) {
        return seenReportIds.contains(reportId);
    }

    private boolean shouldSimulateFailure() {
        return random.nextDouble() < regulatoryProperties.getFailureRate();
    }
}

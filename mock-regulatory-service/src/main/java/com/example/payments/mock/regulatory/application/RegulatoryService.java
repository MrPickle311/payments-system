package com.example.payments.mock.regulatory.application;

import static com.example.payments.mock.regulatory.common.RegulatoryConstants.ACCEPTED_RESPONSE;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.DUPLICATE_RESPONSE;

import com.example.payments.mock.regulatory.application.dto.RegulatoryReportDto;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryService {

    private final Set<String> seenReportIds = new HashSet<>();

    public String processReport(RegulatoryReportDto request) {
        if (isDuplicate(request.getReportId())) {
            return handleDuplicate(request.getReportId());
        }
        log.info(
                "[MockRegulatory] Received report: id={}, count={}",
                request.getReportId(),
                request.getPayments().size());
        return processValidReport(request.getReportId());
    }

    private String handleDuplicate(String reportId) {
        log.warn("[MockRegulatory] IDEMPOTENCY HIT: Already processed reportId={}", reportId);
        return DUPLICATE_RESPONSE;
    }

    private String processValidReport(String reportId) {
        seenReportIds.add(reportId);
        log.info("[MockRegulatory] Report processed successfully");
        return ACCEPTED_RESPONSE;
    }

    private boolean isDuplicate(String reportId) {
        return seenReportIds.contains(reportId);
    }
}

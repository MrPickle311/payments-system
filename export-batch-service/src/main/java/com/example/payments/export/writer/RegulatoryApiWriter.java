package com.example.payments.export.writer;

import static com.example.payments.export.config.ExportConstants.FALLBACK_PREFIX;
import static com.example.payments.export.config.ExportConstants.MD5_ALGORITHM;

import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.dto.RegulatoryReportRequest;
import com.example.payments.export.dto.RegulatoryReportRequest.ExportedPayment;
import com.example.payments.export.mapper.PaymentMapper;
import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@Profile({"manager", "retry-manager"})
@RequiredArgsConstructor
public class RegulatoryApiWriter implements ItemWriter<PaymentExportStaging> {

    private final RestTemplate restTemplate;
    private final ExportProperties exportProperties;
    private final PaymentMapper paymentMapper;
    private final PaymentExportStagingRepository stagingRepository;
    private final Clock clock;

    @Override
    public void write(Chunk<? extends PaymentExportStaging> chunk) {
        RegulatoryReportRequest request = buildRequest(chunk);
        String url = exportProperties.getRegulatory().getUrl();
        log.info("Sending report {} to {}, chunks {}", request.getReportId(), url, chunk.size());
        restTemplate.postForLocation(url, request);
        log.info("Report id={} sent successfully", request.getReportId());
        List<Long> ids =
                chunk.getItems().stream().map(PaymentExportStaging::getId).toList();
        stagingRepository.markAsExported(ids, LocalDateTime.now(clock));
    }

    private RegulatoryReportRequest buildRequest(Chunk<? extends PaymentExportStaging> chunk) {
        List<ExportedPayment> exportedPayments = chunk.getItems().stream()
                .map(paymentMapper::stagingToExportedPayment)
                .toList();
        return RegulatoryReportRequest.builder()
                .reportId(generateReportId(chunk.getItems()))
                .payments(exportedPayments)
                .build();
    }

    private String generateReportId(List<? extends PaymentExportStaging> items) {
        String ids = items.stream()
                .map(PaymentExportStaging::getPaymentId)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return calculateChecksum(ids);
    }

    private String calculateChecksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes());
            return IntStream.range(0, hash.length)
                    .mapToObj(i -> String.format("%02x", hash[i]))
                    .collect(Collectors.joining());
        } catch (Exception e) {
            log.warn("MD5 failed, falling back", e);
            return FALLBACK_PREFIX + input.hashCode();
        }
    }
}

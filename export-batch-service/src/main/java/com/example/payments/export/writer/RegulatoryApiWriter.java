package com.example.payments.export.writer;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.dto.RegulatoryReportRequest;
import com.example.payments.export.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.payments.export.config.ExportConstants.FALLBACK_PREFIX;
import static com.example.payments.export.config.ExportConstants.MD5_ALGORITHM;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryApiWriter implements ItemWriter<LedgerEvent> {

  private final RestTemplate restTemplate;
  private final ExportProperties exportProperties;
  private final PaymentMapper paymentMapper;

  @Override
  public void write(Chunk<? extends LedgerEvent> chunk) {
    RegulatoryReportRequest request = buildRequest(chunk);
    String url = exportProperties.getRegulatory().getUrl();
    log.info("Sending report {} to {}, chunks {}", request.getReportId(), url, chunk.size());
    executePostWithCircuitBreaker(url, request);
    log.info("Report id={} sent successfully", request.getReportId());
  }

  private RegulatoryReportRequest buildRequest(Chunk<? extends LedgerEvent> chunk) {
    List<RegulatoryReportRequest.ExportedPayment> exportedPayments =
        chunk.getItems().stream().map(paymentMapper::toExportedPayment).toList();
    return RegulatoryReportRequest.builder().reportId(generateReportId(chunk.getItems()))
        .payments(exportedPayments).build();
  }

  private String generateReportId(List<? extends LedgerEvent> items) {
    String ids = items.stream().map(LedgerEvent::getPaymentId).sorted().map(String::valueOf)
        .collect(Collectors.joining(","));
    return calculateChecksum(ids);
  }

  private String calculateChecksum(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance(MD5_ALGORITHM);
      byte[] hash = digest.digest(input.getBytes());
      return IntStream.range(0, hash.length).mapToObj(i -> String.format("%02x", hash[i]))
          .collect(Collectors.joining());
    } catch (Exception e) {
      log.warn("MD5 failed, falling back");
      return FALLBACK_PREFIX + input.hashCode();
    }
  }

  private static enum CircuitState { CLOSED, OPEN, HALF_OPEN }
  private static CircuitState state = CircuitState.CLOSED;
  private static int failureCount = 0;
  private static long lastStateChange = 0L;
  private static final int FAILURE_THRESHOLD = 3;
  private static final long COOLDOWN_MS = 10000L;

  private void executePostWithCircuitBreaker(String url, RegulatoryReportRequest request) {
    checkCircuitState();
    if (state == CircuitState.OPEN) {
      log.error("[CircuitBreaker] Call rejected — circuit is OPEN.");
      throw new RuntimeException("Circuit breaker is OPEN. Preventing call to regulatory API.");
    }
    try {
      restTemplate.postForLocation(url, request);
      onSuccess();
    } catch (Exception e) {
      onFailure(e);
      throw e;
    }
  }

  private void checkCircuitState() {
    if (state == CircuitState.OPEN && System.currentTimeMillis() - lastStateChange > COOLDOWN_MS) {
      state = CircuitState.HALF_OPEN;
      log.info("[CircuitBreaker] Transitioning to HALF_OPEN after cooldown.");
    }
  }

  private void onSuccess() {
    if (state == CircuitState.HALF_OPEN) {
      state = CircuitState.CLOSED;
      failureCount = 0;
      log.info("[CircuitBreaker] Call succeeded in HALF_OPEN. Closing circuit.");
    }
  }

  private void onFailure(Exception e) {
    failureCount++;
    log.warn("[CircuitBreaker] Failure detected: {}", e.getMessage());
    if (state == CircuitState.CLOSED && failureCount >= FAILURE_THRESHOLD) {
      state = CircuitState.OPEN;
      lastStateChange = System.currentTimeMillis();
      log.error("[CircuitBreaker] Threshold reached. Opening circuit.");
    }
  }
}

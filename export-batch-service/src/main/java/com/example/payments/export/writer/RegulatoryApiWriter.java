package com.example.payments.export.writer;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.dto.RegulatoryReportRequest;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class RegulatoryApiWriter implements ItemWriter<LedgerEvent> {

  private final RestTemplate restTemplate;
  private final ExportProperties exportProperties;

  @Override
  public void write(Chunk<? extends LedgerEvent> chunk) {
    RegulatoryReportRequest request = buildRequest(chunk);
    String url = exportProperties.getRegulatory().getUrl();
    log.info("Sending report {} to {}, chunks {}", request.getReportId(), url, chunk.size());
    restTemplate.postForLocation(url, request);
    log.info("Report id={} sent successfully", request.getReportId());
  }

  private RegulatoryReportRequest buildRequest(Chunk<? extends LedgerEvent> chunk) {
    List<RegulatoryReportRequest.ExportedPayment> exportedPayments =
        chunk.getItems().stream().map(this::toExportedPayment).toList();
    return RegulatoryReportRequest.builder().reportId(generateReportId(chunk.getItems()))
        .payments(exportedPayments).build();
  }

  private RegulatoryReportRequest.ExportedPayment toExportedPayment(LedgerEvent e) {
    return RegulatoryReportRequest.ExportedPayment.builder().paymentId(e.getPaymentId())
        .grossAmount(e.getGrossAmount()).netAmount(e.getNetAmount()).currency(e.getCurrency())
        .timestamp(e.getTimestamp()).build();
  }

  private String generateReportId(List<? extends LedgerEvent> items) {
    String ids = items.stream().map(LedgerEvent::getPaymentId).sorted().map(String::valueOf)
        .collect(Collectors.joining(","));
    return calculateMd5(ids);
  }

  private String calculateMd5(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] hash = digest.digest(input.getBytes());
      return IntStream.range(0, hash.length).mapToObj(i -> String.format("%02x", hash[i]))
          .collect(Collectors.joining());
    } catch (Exception e) {
      log.warn("MD5 failed, falling back");
      return "fallback-" + input.hashCode();
    }
  }
}

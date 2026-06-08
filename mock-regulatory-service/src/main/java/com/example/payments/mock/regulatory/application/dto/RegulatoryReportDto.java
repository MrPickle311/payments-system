package com.example.payments.mock.regulatory.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryReportDto {
  private String reportId;
  private List<ExportedPaymentDto> payments;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ExportedPaymentDto {
    private Long paymentId;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private String currency;
    private LocalDateTime timestamp;
  }
}

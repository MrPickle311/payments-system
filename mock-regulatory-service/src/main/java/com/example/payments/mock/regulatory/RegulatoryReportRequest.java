package com.example.payments.mock.regulatory;

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
public class RegulatoryReportRequest {
    private String reportId;
    private List<ExportedPayment> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportedPayment {
        private Long paymentId;
        private BigDecimal grossAmount;
        private BigDecimal netAmount;
        private String currency;
        private LocalDateTime timestamp;
    }
}

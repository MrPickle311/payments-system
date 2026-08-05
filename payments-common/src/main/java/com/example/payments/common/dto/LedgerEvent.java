package com.example.payments.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEvent {
    private Long paymentId;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private String currency;
    private LocalDateTime timestamp;
}

package com.example.payments.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

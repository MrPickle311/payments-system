package com.example.payments.fee.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted fee breakdown.
 */
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.jmolecules.ddd.annotation.Entity
public class PaymentFee {

    private Long id;
    private Long paymentId;
    private BigDecimal grossAmount;
    private BigDecimal percentageFee;
    private BigDecimal flatFee;
    private BigDecimal totalFee;
    private BigDecimal netAmount;
    private String currency;
    private LocalDateTime createdAt;
}

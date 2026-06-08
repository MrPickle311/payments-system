package com.example.payments.fee.domain;

import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.jmolecules.ddd.annotation.Entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
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

package com.example.payments.fraud.domain;

import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@org.jmolecules.ddd.annotation.Entity
public class FraudRecord {

  private Long id;
  private Long paymentId;
  private Integer score;
  private String riskLevel;
  private String recommendation;
  private LocalDateTime createdAt;
}



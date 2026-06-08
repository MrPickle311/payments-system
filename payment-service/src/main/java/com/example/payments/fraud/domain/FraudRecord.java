package com.example.payments.fraud.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jmolecules.ddd.annotation.Entity;

@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class FraudRecord {

  private Long id;
  private Long paymentId;
  private Integer score;
  private String riskLevel;
  private String recommendation;
  private LocalDateTime createdAt;
}



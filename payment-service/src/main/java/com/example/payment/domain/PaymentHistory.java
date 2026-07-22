package com.example.payment.domain;

import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.jmolecules.ddd.annotation.Entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class PaymentHistory {

  private Long id;
  private Long paymentId;

  @Builder.Default
  private String region = "ROOT";
  private String fromState;
  private String toState;
  private String event;

  @Builder.Default
  private LocalDateTime timestamp = LocalDateTime.now(ZoneId.systemDefault());
}

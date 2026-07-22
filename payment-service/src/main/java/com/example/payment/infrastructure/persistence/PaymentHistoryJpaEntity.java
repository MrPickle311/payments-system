package com.example.payment.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "region", nullable = false, length = 100)
  private String region;

  @Column(name = "from_state", length = 255)
  private String fromState;

  @Column(name = "to_state", nullable = false, length = 255)
  private String toState;

  @Column(nullable = false, length = 255)
  private String event;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime timestamp;
}

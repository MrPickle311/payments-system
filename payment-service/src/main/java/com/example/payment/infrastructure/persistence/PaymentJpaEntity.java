package com.example.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String state;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Column(name = "fraud_risk", length = 50)
    private String fraudRisk;

    @Column(name = "source_user_id")
    private Long sourceUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "source_currency", length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", length = 3)
    private String targetCurrency;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal processingFee;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;//TODO: convert to offsetdatetime

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}

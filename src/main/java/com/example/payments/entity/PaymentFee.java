package com.example.payments.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted fee breakdown written by the {@code settlementAction} when a payment
 * transitions AUTHORIZED → COMPLETED.  One record per payment (unique constraint
 * on {@code payment_id}).
 */
@Entity
@Table(name = "payment_fees", indexes = {
        @Index(name = "idx_payment_fees_payment_id", columnList = "payment_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    /** The original charged amount (before fees). */
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    /** Percentage component: grossAmount × 2.9%. */
    @Column(name = "percentage_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal percentageFee;

    /** Fixed per-transaction component: $0.30. */
    @Column(name = "flat_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal flatFee;

    /** Total fee deducted: percentageFee + flatFee. */
    @Column(name = "total_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalFee;

    /** Amount remitted to the merchant: grossAmount − totalFee. */
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

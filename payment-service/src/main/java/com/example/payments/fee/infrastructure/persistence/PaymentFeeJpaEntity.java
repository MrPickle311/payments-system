package com.example.payments.fee.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_fees", indexes = {
        @Index(name = "idx_payment_fees_payment_id", columnList = "payment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFeeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "percentage_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal percentageFee;

    @Column(name = "flat_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal flatFee;

    @Column(name = "total_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalFee;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

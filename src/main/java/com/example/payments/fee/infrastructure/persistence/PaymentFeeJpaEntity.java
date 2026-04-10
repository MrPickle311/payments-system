package com.example.payments.fee.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_fees", indexes = {
        @Index(name = "idx_payment_fees_payment_id", columnList = "payment_id")
})
@ToString
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

    public PaymentFeeJpaEntity() {}

    public PaymentFeeJpaEntity(Long id, Long paymentId, BigDecimal grossAmount, BigDecimal percentageFee, BigDecimal flatFee, BigDecimal totalFee, BigDecimal netAmount, String currency, LocalDateTime createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.grossAmount = grossAmount;
        this.percentageFee = percentageFee;
        this.flatFee = flatFee;
        this.totalFee = totalFee;
        this.netAmount = netAmount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getPercentageFee() { return percentageFee; }
    public void setPercentageFee(BigDecimal percentageFee) { this.percentageFee = percentageFee; }
    public BigDecimal getFlatFee() { return flatFee; }
    public void setFlatFee(BigDecimal flatFee) { this.flatFee = flatFee; }
    public BigDecimal getTotalFee() { return totalFee; }
    public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class PaymentFeeJpaEntityBuilder {
        private Long id;
        private Long paymentId;
        private BigDecimal grossAmount;
        private BigDecimal percentageFee;
        private BigDecimal flatFee;
        private BigDecimal totalFee;
        private BigDecimal netAmount;
        private String currency;
        private LocalDateTime createdAt;

        public PaymentFeeJpaEntityBuilder id(Long id) { this.id = id; return this; }
        public PaymentFeeJpaEntityBuilder paymentId(Long paymentId) { this.paymentId = paymentId; return this; }
        public PaymentFeeJpaEntityBuilder grossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; return this; }
        public PaymentFeeJpaEntityBuilder percentageFee(BigDecimal percentageFee) { this.percentageFee = percentageFee; return this; }
        public PaymentFeeJpaEntityBuilder flatFee(BigDecimal flatFee) { this.flatFee = flatFee; return this; }
        public PaymentFeeJpaEntityBuilder totalFee(BigDecimal totalFee) { this.totalFee = totalFee; return this; }
        public PaymentFeeJpaEntityBuilder netAmount(BigDecimal netAmount) { this.netAmount = netAmount; return this; }
        public PaymentFeeJpaEntityBuilder currency(String currency) { this.currency = currency; return this; }
        public PaymentFeeJpaEntityBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public PaymentFeeJpaEntity build() {
            return new PaymentFeeJpaEntity(id, paymentId, grossAmount, percentageFee, flatFee, totalFee, netAmount, currency, createdAt);
        }
    }

    public static PaymentFeeJpaEntityBuilder builder() {
        return new PaymentFeeJpaEntityBuilder();
    }
}

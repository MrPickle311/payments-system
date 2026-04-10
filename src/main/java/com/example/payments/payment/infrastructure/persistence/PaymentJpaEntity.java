package com.example.payments.payment.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@ToString
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentJpaEntity() {}

    public PaymentJpaEntity(Long id, String transactionId, BigDecimal amount, String currency, String state, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static class PaymentJpaEntityBuilder {
        private Long id;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private String state;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public PaymentJpaEntityBuilder id(Long id) { this.id = id; return this; }
        public PaymentJpaEntityBuilder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public PaymentJpaEntityBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public PaymentJpaEntityBuilder currency(String currency) { this.currency = currency; return this; }
        public PaymentJpaEntityBuilder state(String state) { this.state = state; return this; }
        public PaymentJpaEntityBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PaymentJpaEntityBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PaymentJpaEntity build() {
            return new PaymentJpaEntity(id, transactionId, amount, currency, state, createdAt, updatedAt);
        }
    }

    public static PaymentJpaEntityBuilder builder() {
        return new PaymentJpaEntityBuilder();
    }
}

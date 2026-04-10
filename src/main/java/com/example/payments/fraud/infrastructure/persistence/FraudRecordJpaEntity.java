package com.example.payments.fraud.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_records")
@ToString
public class FraudRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    private Integer score;

    @Column(name = "risk_level")
    private String riskLevel;

    private String recommendation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public FraudRecordJpaEntity() {}

    public FraudRecordJpaEntity(Long id, Long paymentId, Integer score, String riskLevel, String recommendation, LocalDateTime createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.score = score;
        this.riskLevel = riskLevel;
        this.recommendation = recommendation;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class FraudRecordJpaEntityBuilder {
        private Long id;
        private Long paymentId;
        private Integer score;
        private String riskLevel;
        private String recommendation;
        private LocalDateTime createdAt;

        public FraudRecordJpaEntityBuilder id(Long id) { this.id = id; return this; }
        public FraudRecordJpaEntityBuilder paymentId(Long paymentId) { this.paymentId = paymentId; return this; }
        public FraudRecordJpaEntityBuilder score(Integer score) { this.score = score; return this; }
        public FraudRecordJpaEntityBuilder riskLevel(String riskLevel) { this.riskLevel = riskLevel; return this; }
        public FraudRecordJpaEntityBuilder recommendation(String recommendation) { this.recommendation = recommendation; return this; }
        public FraudRecordJpaEntityBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public FraudRecordJpaEntity build() {
            return new FraudRecordJpaEntity(id, paymentId, score, riskLevel, recommendation, createdAt);
        }
    }

    public static FraudRecordJpaEntityBuilder builder() {
        return new FraudRecordJpaEntityBuilder();
    }
}

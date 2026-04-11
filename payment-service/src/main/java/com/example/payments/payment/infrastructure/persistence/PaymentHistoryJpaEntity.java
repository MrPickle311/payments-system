package com.example.payments.payment.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history", indexes = {
        @Index(name = "idx_payment_history_payment_id", columnList = "payment_id")
})
@ToString
public class PaymentHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(nullable = false)
    private String event;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public PaymentHistoryJpaEntity() {}

    public PaymentHistoryJpaEntity(Long id, Long paymentId, String fromState, String toState, String event, LocalDateTime timestamp) {
        this.id = id;
        this.paymentId = paymentId;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static class PaymentHistoryJpaEntityBuilder {
        private Long id;
        private Long paymentId;
        private String fromState;
        private String toState;
        private String event;
        private LocalDateTime timestamp;

        public PaymentHistoryJpaEntityBuilder id(Long id) { this.id = id; return this; }
        public PaymentHistoryJpaEntityBuilder paymentId(Long paymentId) { this.paymentId = paymentId; return this; }
        public PaymentHistoryJpaEntityBuilder fromState(String fromState) { this.fromState = fromState; return this; }
        public PaymentHistoryJpaEntityBuilder toState(String toState) { this.toState = toState; return this; }
        public PaymentHistoryJpaEntityBuilder event(String event) { this.event = event; return this; }
        public PaymentHistoryJpaEntityBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public PaymentHistoryJpaEntity build() {
            return new PaymentHistoryJpaEntity(id, paymentId, fromState, toState, event, timestamp);
        }
    }

    public static PaymentHistoryJpaEntityBuilder builder() {
        return new PaymentHistoryJpaEntityBuilder();
    }
}

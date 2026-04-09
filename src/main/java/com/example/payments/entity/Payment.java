package com.example.payments.entity;

import com.example.payments.enums.PaymentState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core payment aggregate root.
 *
 * The {@code state} column is persisted as a String (the enum name) so that
 * adding new states never requires a DB migration. The state machine is the
 * sole authority for legal transitions; nothing else should mutate {@code state}
 * directly.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * External reference from the payment gateway (e.g. Stripe PaymentIntent ID).
     * Unique constraint prevents duplicate webhook processing for the same transaction.
     */
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** ISO 4217 currency code, e.g. "USD", "EUR". */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Current state of this payment. Stored as the enum name string.
     * Never mutate directly — always drive changes through the state machine.
     */
    @Column(nullable = false)
    @Builder.Default
    private String state = PaymentState.NEW.name();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Convenience accessor that parses the stored string back to the enum. */
    @Transient
    public PaymentState currentState() {
        return PaymentState.valueOf(this.state);
    }
}

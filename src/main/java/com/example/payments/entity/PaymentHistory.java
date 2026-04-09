package com.example.payments.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record written by {@link com.example.payments.interceptor.PaymentStateMachineInterceptor}
 * after every successful state transition.
 *
 * The table is append-only by convention; records are never updated or deleted
 * so that the full transition trail is always preserved for compliance and debugging.
 */
@Entity
@Table(name = "payment_history", indexes = {
        @Index(name = "idx_payment_history_payment_id", columnList = "payment_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to the parent {@link Payment}. Stored as a plain column for simplicity. */
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    /** The event that triggered this transition. */
    @Column(nullable = false)
    private String event;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

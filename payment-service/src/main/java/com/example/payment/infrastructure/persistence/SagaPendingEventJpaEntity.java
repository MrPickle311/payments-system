package com.example.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "saga_pending_events",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_saga_pending_events_payment_join",
                        columnNames = {"payment_id", "join_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaPendingEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "event", nullable = false)
    private String event;

    /** Unique together with {@code payment_id}; this pair is the durable join guard. */
    @Column(name = "join_key", nullable = false)
    private String joinKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    /** Identifies which pod currently owns re-driving this row; null when nobody does. */
    @Column(name = "locked_by")
    private String lockedBy;

    /** Lease expiry. A row is takeable when this is null or in the past. */
    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

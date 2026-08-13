package com.example.payment.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByTransactionId(String transactionId);

    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    Optional<PaymentJpaEntity> findByIdWithLock(@Param("id") Long id);

    /**
     * Payments that have not reached a terminal state and have not moved since {@code threshold}.
     *
     * <p>Previously this matched {@code p.state IN ('PROCESSING','FX_CONVERSION','INITIATE')}. That
     * could essentially never match a real mid-flight payment: {@code state} holds the whole
     * orthogonal state set comma-joined (e.g. {@code PROCESSING,AUTH_APPROVED,FRAUD_PASSED,...}),
     * so an equality test against {@code 'PROCESSING'} only matched the instant before any region
     * had advanced. It also omitted SETTLEMENT and COMPENSATING entirely, and {@code 'INITIATE'} is
     * an event name, not a state — there is no such {@code PaymentState} constant.
     *
     * <p>Inverted to exclude terminal states instead of enumerating live ones, so new composite
     * states cannot silently fall out of the sweep. COMPLETED and FAILED have no regions, so their
     * persisted form is always exactly the bare state name.
     */
    @Query("""
            SELECT p FROM PaymentJpaEntity p
            WHERE p.state NOT IN ('COMPLETED', 'FAILED')
              AND p.updatedAt < :threshold
            ORDER BY p.updatedAt
            LIMIT :batchSize
            """)
    List<PaymentJpaEntity> findStuckPayments(
            @Param("threshold") OffsetDateTime threshold, @Param("batchSize") int batchSize);
}

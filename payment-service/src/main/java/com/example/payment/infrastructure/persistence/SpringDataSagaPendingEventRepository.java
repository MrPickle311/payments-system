package com.example.payment.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataSagaPendingEventRepository extends JpaRepository<SagaPendingEventJpaEntity, Long> {

    Optional<SagaPendingEventJpaEntity> findByPaymentIdAndJoinKey(Long paymentId, String joinKey);

    /** Excludes leased rows as a fast path; {@link #tryLease} is the real guarantee. */
    @Query("""
            SELECT e FROM SagaPendingEventJpaEntity e
            WHERE e.status = 'PENDING'
              AND e.createdAt < :threshold
              AND (e.lockedUntil IS NULL OR e.lockedUntil < :now)
            ORDER BY e.createdAt
            LIMIT :batchSize
            """)
    List<SagaPendingEventJpaEntity> findStalePending(
            @Param("threshold") OffsetDateTime threshold,
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize);

    /**
     * Conditional update: only takes the lease if nobody holds it or the holder's lease expired.
     *
     * @return 1 if this caller now owns the row, 0 if another pod won the race first.
     */
    @Modifying
    @Query("""
            UPDATE SagaPendingEventJpaEntity e
            SET e.lockedBy = :instanceId, e.lockedUntil = :leaseUntil
            WHERE e.id = :id
              AND (e.lockedUntil IS NULL OR e.lockedUntil < :now)
            """)
    int tryLease(
            @Param("id") Long id,
            @Param("instanceId") String instanceId,
            @Param("leaseUntil") OffsetDateTime leaseUntil,
            @Param("now") OffsetDateTime now);
}

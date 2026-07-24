package com.example.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByTransactionId(String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    Optional<PaymentJpaEntity> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("""
            SELECT p FROM PaymentJpaEntity p
            WHERE p.state = 'PROCESSING'
              AND p.updatedAt < :threshold
            ORDER BY p.updatedAt
            LIMIT :batchSize
            """)
    List<PaymentJpaEntity> findStuckPaymentsForUpdate(
            @Param("threshold") LocalDateTime threshold,
            @Param("batchSize") int batchSize);
}

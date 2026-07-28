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

    @Query("""
            SELECT p FROM PaymentJpaEntity p
            WHERE p.state IN ('PROCESSING', 'FX_CONVERSION','INITIATE')
              AND p.updatedAt < :threshold
            ORDER BY p.updatedAt
            LIMIT :batchSize
            """)
    List<PaymentJpaEntity> findStuckPayments(
            @Param("threshold") OffsetDateTime threshold,
            @Param("batchSize") int batchSize);
}

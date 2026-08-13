package com.example.payments.ledger.infrastructure.persistence;

import com.example.payments.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Plain save() always inserts a new row (auto-generated id, no natural key check), so a
     * redelivered Kafka message would double-post the same payment. ON CONFLICT DO NOTHING
     * claims payment_id atomically without throwing: a caught DataIntegrityViolationException
     * would still leave the underlying Postgres transaction aborted, failing every later
     * statement in the same @Transactional method.
     */
    @Modifying
    @Query(value = """
        INSERT INTO ledger_entries (payment_id, gross_amount, net_amount, currency, timestamp)
        VALUES (:paymentId, :grossAmount, :netAmount, :currency, :timestamp)
        ON CONFLICT (payment_id) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(
            @Param("paymentId") Long paymentId,
            @Param("grossAmount") BigDecimal grossAmount,
            @Param("netAmount") BigDecimal netAmount,
            @Param("currency") String currency,
            @Param("timestamp") LocalDateTime timestamp);
}

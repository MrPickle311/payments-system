package com.example.payments.repository;

import com.example.payments.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Fetches a payment and immediately acquires a pessimistic write lock on
     * the database row.  This prevents two concurrent requests (e.g. two
     * simultaneous webhooks for the same payment) from reading the same state
     * and producing conflicting transitions.
     *
     * <p>Must be called within a {@code @Transactional} method; the lock is
     * held until the surrounding transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);
}

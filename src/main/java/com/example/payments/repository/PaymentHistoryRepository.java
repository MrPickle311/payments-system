package com.example.payments.repository;

import com.example.payments.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    /** Returns the full audit trail for a payment, ordered chronologically. */
    List<PaymentHistory> findByPaymentIdOrderByTimestampAsc(Long paymentId);
}

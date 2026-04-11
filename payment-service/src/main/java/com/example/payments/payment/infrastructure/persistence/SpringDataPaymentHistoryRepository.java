package com.example.payments.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataPaymentHistoryRepository extends JpaRepository<PaymentHistoryJpaEntity, Long> {

    List<PaymentHistoryJpaEntity> findByPaymentIdOrderByTimestampAsc(Long paymentId);
}

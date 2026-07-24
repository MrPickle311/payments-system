package com.example.payment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPaymentHistoryRepository extends JpaRepository<PaymentHistoryJpaEntity, Long> {

    List<PaymentHistoryJpaEntity> findByPaymentIdOrderByTimestampAsc(Long paymentId);
}

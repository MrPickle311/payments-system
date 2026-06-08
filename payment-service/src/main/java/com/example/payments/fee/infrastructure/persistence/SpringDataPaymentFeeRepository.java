package com.example.payments.fee.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataPaymentFeeRepository extends JpaRepository<PaymentFeeJpaEntity, Long> {
  Optional<PaymentFeeJpaEntity> findByPaymentId(Long paymentId);
}

package com.example.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
  Optional<Payment> findById(Long id);

  Optional<Payment> findByIdWithLock(Long id);

  Optional<Payment> findByTransactionId(String transactionId);

  Payment save(Payment payment);

  boolean existsById(Long id);
}

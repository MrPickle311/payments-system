package com.example.payments.payment.domain;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
  Optional<Payment> findById(Long id);

  Optional<Payment> findByIdWithLock(Long id);

  Optional<Payment> findByTransactionId(String transactionId);

  List<Payment> findByState(String state);

  Payment save(Payment payment);

  boolean existsById(Long id);
}

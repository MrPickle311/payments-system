package com.example.payments.fee.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentFeeRepository {
  PaymentFee save(PaymentFee paymentFee);

  Optional<PaymentFee> findByPaymentId(Long paymentId);

  BigDecimal getSumOfFeesSince(LocalDateTime since);
}

package com.example.payments.fee.domain;

import java.util.Optional;

public interface PaymentFeeRepository {
  PaymentFee save(PaymentFee paymentFee);

  Optional<PaymentFee> findByPaymentId(Long paymentId);
}

package com.example.fraud.application;


import com.example.payments.common.sharedkernel.Money;

public interface FraudCheckPort {
  FraudResult evaluate(Long paymentId, Money money);

}

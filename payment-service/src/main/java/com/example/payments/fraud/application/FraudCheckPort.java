package com.example.payments.fraud.application;


import com.example.payments.sharedkernel.Money;

public interface FraudCheckPort {
  FraudResult evaluate(Long paymentId, Money money);

  record FraudResult(Integer score, String riskLevel, String recommendation) {
    public boolean isHighRisk() {
      return "HIGH".equalsIgnoreCase(riskLevel);
    }
  }
}

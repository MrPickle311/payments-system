package com.example.payment.domain;

public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(Long paymentId) {
    super("Payment not found: id=" + paymentId);
  }
}

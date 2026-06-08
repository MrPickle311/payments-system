package com.example.payments.payment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentTest {

  @Test
  void testCurrentStateNull() {
    Payment payment = Payment.builder().state(null).build();
    assertNull(payment.currentState());
  }
}

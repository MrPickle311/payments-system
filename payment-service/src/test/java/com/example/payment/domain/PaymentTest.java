package com.example.payment.domain;

import com.example.payment.domain.enums.PaymentState;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTest {

  @Test
  void testCurrentStateNull() {
    Payment payment = Payment.builder().state(null).build();
    assertNull(payment.currentState());
  }

  @Test
  void testIsTerminal() {
    Payment nonTerminal = Payment.builder().state(PaymentState.NEW.name()).build();
    assertFalse(nonTerminal.isTerminal());

    Payment completed = Payment.builder().state(PaymentState.COMPLETED.name()).build();
    assertTrue(completed.isTerminal());

    Payment failed = Payment.builder().state(PaymentState.FAILED.name()).build();
    assertTrue(failed.isTerminal());

    Payment canceled = Payment.builder().state(PaymentState.CANCELED.name()).build();
    assertTrue(canceled.isTerminal());

    Payment refunded = Payment.builder().state(PaymentState.REFUNDED.name()).build();
    assertTrue(refunded.isTerminal());
  }

  @Test
  void testUpdateFinancialDetails() {
    Payment payment = Payment.builder().build();
    payment.updateFinancialDetails(new BigDecimal("2.50"), new BigDecimal("97.50"));

    assertEquals(new BigDecimal("2.50"), payment.getProcessingFee());
    assertEquals(new BigDecimal("97.50"), payment.getNetAmount());
  }

  @Test
  void testMarkFraudEvaluation() {
    Payment payment = Payment.builder().build();
    payment.markFraudEvaluation(85, "HIGH");

    assertEquals(85, payment.getFraudScore());
    assertEquals("HIGH", payment.getFraudRisk());
  }
}

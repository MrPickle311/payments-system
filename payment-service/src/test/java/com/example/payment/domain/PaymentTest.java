package com.example.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.payment.domain.enums.PaymentState;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final String FEE_2_50 = "2.50";
    private static final String NET_97_50 = "97.50";
    private static final String RISK_HIGH = "HIGH";

    @Test
    void testCurrentStateNull() {
        Payment payment = Payment.builder().state(null).build();
        assertNull(payment.currentState());
    }

    @Test
    void testIsTerminal() {
        assertFalse(Payment.builder().state(PaymentState.NEW.name()).build().isTerminal());
        assertTrue(
                Payment.builder().state(PaymentState.COMPLETED.name()).build().isTerminal());
        assertTrue(Payment.builder().state(PaymentState.FAILED.name()).build().isTerminal());
    }

    @Test
    void testUpdateFinancialDetails() {
        Payment payment = Payment.builder().build();
        payment.updateFinancialDetails(new BigDecimal(FEE_2_50), new BigDecimal(NET_97_50));

        assertEquals(new BigDecimal(FEE_2_50), payment.getProcessingFee());
        assertEquals(new BigDecimal(NET_97_50), payment.getNetAmount());
    }

    @Test
    void testMarkFraudEvaluation() {
        Payment payment = Payment.builder().build();
        payment.markFraudEvaluation(85, RISK_HIGH);

        assertEquals(85, payment.getFraudScore());
        assertEquals(RISK_HIGH, payment.getFraudRisk());
    }
}

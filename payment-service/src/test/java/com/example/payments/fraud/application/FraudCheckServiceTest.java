package com.example.payments.fraud.application;

import com.example.payments.fraud.domain.FraudRecord;
import com.example.payments.fraud.domain.FraudRecordRepository;
import com.example.payments.fraud.application.FraudCheckPort.FraudResult;
import com.example.payments.payment.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

  @Mock
  private FraudRecordRepository fraudRecordRepository;

  private FraudCheckService fraudCheckService;

  @BeforeEach
  void setUp() {
    fraudCheckService = new FraudCheckService(fraudRecordRepository);
  }

  private static final String USD = "USD";
  private static final String LOW = "LOW";
  private static final String ALLOW = "ALLOW";

  @Test
  void testEvaluateLowRisk() {
    Money money = Money.of(new BigDecimal("100.00"), USD);
    Long paymentId = 1L;

    FraudResult result = fraudCheckService.evaluate(paymentId, money);

    assertNotNull(result);
    assertEquals(10, result.score());
    assertEquals(LOW, result.riskLevel());
    assertEquals(ALLOW, result.recommendation());
    assertFalse(result.isHighRisk());
    verifyFraudRecord(paymentId);
  }

  private void verifyFraudRecord(Long paymentId) {
    ArgumentCaptor<FraudRecord> captor = ArgumentCaptor.forClass(FraudRecord.class);
    verify(fraudRecordRepository).save(captor.capture());
    FraudRecord fraudRecord = captor.getValue();
    assertEquals(paymentId, fraudRecord.getPaymentId());
    assertEquals(10, fraudRecord.getScore());
    assertEquals(LOW, fraudRecord.getRiskLevel());
    assertEquals(ALLOW, fraudRecord.getRecommendation());
  }

  @Test
  void testEvaluateMediumRisk() {
    Money money = Money.of(new BigDecimal("1500.00"), USD);
    Long paymentId = 2L;

    FraudResult result = fraudCheckService.evaluate(paymentId, money);

    assertNotNull(result);
    assertEquals(55, result.score());
    assertEquals("MEDIUM", result.riskLevel());
    assertEquals(ALLOW, result.recommendation());
    assertFalse(result.isHighRisk());
    verify(fraudRecordRepository).save(any(FraudRecord.class));
  }

  @Test
  void testEvaluateHighRisk() {
    Money money = Money.of(new BigDecimal("5500.00"), USD);
    Long paymentId = 3L;

    FraudResult result = fraudCheckService.evaluate(paymentId, money);

    assertNotNull(result);
    assertEquals(92, result.score());
    assertEquals("HIGH", result.riskLevel());
    assertEquals("BLOCK", result.recommendation());
    assertTrue(result.isHighRisk());
    verify(fraudRecordRepository).save(any(FraudRecord.class));
  }

  @Test
  void testEvaluateMediumRisk2() {
    Money money = Money.of(new BigDecimal("800.00"), USD);
    Long paymentId = 4L;

    FraudResult result = fraudCheckService.evaluate(paymentId, money);

    assertNotNull(result);
    assertEquals(25, result.score());
    assertEquals(LOW, result.riskLevel());
    assertEquals(ALLOW, result.recommendation());
    assertFalse(result.isHighRisk());
    verify(fraudRecordRepository).save(any(FraudRecord.class));
  }
}

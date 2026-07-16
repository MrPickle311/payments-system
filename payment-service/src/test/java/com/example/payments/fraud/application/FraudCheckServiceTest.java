package com.example.payments.fraud.application;

import com.example.payments.fraud.domain.FraudRecord;
import com.example.payments.fraud.domain.FraudRecordRepository;
import com.example.payments.fraud.application.FraudCheckPort.FraudResult;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.sharedkernel.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

  @Mock
  private FraudRecordRepository fraudRecordRepository;
  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private PayerProfileRepository payerProfileRepository;

  private FraudCheckService fraudCheckService;

  @BeforeEach
  void setUp() {
    fraudCheckService = new FraudCheckService(fraudRecordRepository, paymentRepository, payerProfileRepository);
  }

  private void mockStandardPayment(Long paymentId) {
    Payment p = new Payment();
    p.setId(paymentId);
    p.setTransactionId("std_" + paymentId);
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(p));
    PayerProfile profile = PayerProfile.builder().payerId(1L).segment("STANDARD").kycStatus("VERIFIED").build();
    when(payerProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
  }

  private static final String USD = "USD";
  private static final String LOW = "LOW";
  private static final String ALLOW = "ALLOW";

  @Test
  void testEvaluateLowRisk() {
    Money money = Money.of(new BigDecimal("100.00"), USD);
    Long paymentId = 1L;
    mockStandardPayment(paymentId);
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
    mockStandardPayment(paymentId);
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
    mockStandardPayment(paymentId);
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
    mockStandardPayment(paymentId);
    FraudResult result = fraudCheckService.evaluate(paymentId, money);
    assertNotNull(result);
    assertEquals(25, result.score());
    assertEquals(LOW, result.riskLevel());
    assertEquals(ALLOW, result.recommendation());
    assertFalse(result.isHighRisk());
    verify(fraudRecordRepository).save(any(FraudRecord.class));
  }

  @Test
  void testBasicSegmentSingleLimitExceeded() {
    Payment p = new Payment();
    p.setId(5L);
    p.setTransactionId("basic_123");
    when(paymentRepository.findById(5L)).thenReturn(Optional.of(p));
    Money money = Money.of(new BigDecimal("501.00"), USD);
    FraudResult res = fraudCheckService.evaluate(5L, money);
    assertEquals(100, res.score());
    assertEquals("HIGH", res.riskLevel());
    assertEquals("LIMIT_EXCEEDED", res.recommendation());
  }

  @Test
  void testBasicSegmentAccumulatedLimitExceeded() {
    Payment p = new Payment();
    p.setId(6L);
    p.setTransactionId("basic_456");
    when(paymentRepository.findById(6L)).thenReturn(Optional.of(p));
    when(paymentRepository.getSumOfCompletedPaymentsSince(any())).thenReturn(new BigDecimal("1900.00"));
    Money money = Money.of(new BigDecimal("101.00"), USD);
    FraudResult res = fraudCheckService.evaluate(6L, money);
    assertEquals(100, res.score());
    assertEquals("LIMIT_EXCEEDED", res.recommendation());
  }

  @Test
  void testEvaluateSanctionedPayer() {
    Payment p = new Payment();
    p.setId(7L);
    p.setTransactionId("sanctioned_buyer_123");
    when(paymentRepository.findById(7L)).thenReturn(Optional.of(p));
    Money money = Money.of(new BigDecimal("100.00"), USD);
    FraudResult res = fraudCheckService.evaluate(7L, money);
    assertEquals(100, res.score());
    assertEquals("HIGH", res.riskLevel());
    assertEquals("SANCTIONS_BLOCK", res.recommendation());
  }
}

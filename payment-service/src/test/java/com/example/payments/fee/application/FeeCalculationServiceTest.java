package com.example.payments.fee.application;

import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.fee.domain.PaymentFeeRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeCalculationServiceTest {

  @Mock
  private PaymentFeeRepository paymentFeeRepository;
  @Mock
  private PaymentRepository paymentRepository;

  private FeeCalculationService feeCalculationService;

  @BeforeEach
  void setUp() {
    feeCalculationService = new FeeCalculationService(paymentFeeRepository, paymentRepository);
  }

  private static final String USD = "USD";
  private static final String HUNDRED = "100.00";
  private static final String PERCENT_FEE = "2.9000";
  private static final String FLAT_FEE = "0.30";
  private static final String TOTAL_FEE = "3.2000";
  private static final String NET_AMOUNT = "96.8000";


  @Test
  void testCalculateFee() {
    Money gross = Money.of(new BigDecimal(HUNDRED), USD);
    FeeBreakdown breakdown = feeCalculationService.calculate(gross);

    assertNotNull(breakdown);
    assertEquals(new BigDecimal(HUNDRED), breakdown.grossAmount().amount());
    assertEquals(new BigDecimal(PERCENT_FEE), breakdown.percentageFee().amount());
    assertEquals(new BigDecimal(FLAT_FEE), breakdown.flatFee().amount());
    assertEquals(new BigDecimal(TOTAL_FEE), breakdown.totalFee().amount());
    assertEquals(new BigDecimal(NET_AMOUNT), breakdown.netAmount().amount());
  }

  @Test
  void testSaveSettlement() {
    Money gross = Money.of(new BigDecimal(HUNDRED), USD);
    Long paymentId = 1L;

    PaymentFee savedFee = new PaymentFee();
    when(paymentFeeRepository.save(any(PaymentFee.class))).thenReturn(savedFee);

    feeCalculationService.saveSettlement(paymentId, gross);
    verifySavedFee(paymentId);
  }

  private void verifySavedFee(Long paymentId) {
    ArgumentCaptor<PaymentFee> captor = ArgumentCaptor.forClass(PaymentFee.class);
    verify(paymentFeeRepository).save(captor.capture());
    PaymentFee captured = captor.getValue();
    assertEquals(paymentId, captured.getPaymentId());
    assertEquals(new BigDecimal(HUNDRED), captured.getGrossAmount());
    assertEquals(new BigDecimal(PERCENT_FEE), captured.getPercentageFee());
    assertEquals(new BigDecimal(FLAT_FEE), captured.getFlatFee());
    assertEquals(new BigDecimal(TOTAL_FEE), captured.getTotalFee());
    assertEquals(new BigDecimal(NET_AMOUNT), captured.getNetAmount());
    assertEquals(USD, captured.getCurrency());
  }

  @Test
  void testGetFeeSuccess() {
    PaymentFee fee = new PaymentFee();
    fee.setId(10L);
    fee.setPaymentId(1L);
    when(paymentFeeRepository.findByPaymentId(1L)).thenReturn(Optional.of(fee));

    var result = feeCalculationService.getFee(1L);
    assertEquals(10L, result.id());
    assertEquals(1L, result.paymentId());
  }

  @Test
  void testGetFeeNotFound() {
    when(paymentFeeRepository.findByPaymentId(1L)).thenReturn(Optional.empty());

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> feeCalculationService.getFee(1L));
    assertTrue(ex.getMessage().contains("No fee record found"));
  }

  @Test
  void testCalculateFxSameCurrency() {
    var details = feeCalculationService.calculateFx(new BigDecimal("100.00"), "USD", "USD");
    assertEquals("USD", details.sourceCurrency());
    assertEquals(new BigDecimal("100.00"), details.sourceAmount());
    assertEquals(BigDecimal.ONE, details.exchangeRate());
  }

  @Test
  void testCalculateFxDifferentCurrency() {
    var details = feeCalculationService.calculateFx(new BigDecimal("100.00"), "USD", "EUR");
    assertEquals("USD", details.sourceCurrency());
    assertEquals(new BigDecimal("111.1000"), details.sourceAmount());
    assertEquals(new BigDecimal("1.111000"), details.exchangeRate());
  }

  @Test
  void testCalculateFeeDegressive() {
    Money gross = Money.of(new BigDecimal("2000.00"), USD);
    FeeBreakdown breakdown = feeCalculationService.calculate(gross, null);
    assertEquals(new BigDecimal("2000.00"), breakdown.grossAmount().amount());
    assertEquals(new BigDecimal("30.0000"), breakdown.percentageFee().amount());
    assertEquals(new BigDecimal("0.20"), breakdown.flatFee().amount());
    assertEquals(new BigDecimal("30.2000"), breakdown.totalFee().amount());
  }

  @Test
  void testCalculateFeeWaiver() {
    Payment p = new Payment();
    p.setTransactionId("vip_buyer_123");
    when(paymentRepository.findById(10L)).thenReturn(Optional.of(p));
    Money gross = Money.of(new BigDecimal("100.00"), USD);
    FeeBreakdown breakdown = feeCalculationService.calculate(gross, 10L);
    assertEquals(BigDecimal.ZERO, breakdown.totalFee().amount());
  }

  @Test
  void testGenerateFeeReport() {
    LocalDateTime since = LocalDateTime.now().minusDays(1);
    when(paymentFeeRepository.getSumOfFeesSince(since)).thenReturn(new BigDecimal("150.00"));
    BigDecimal total = feeCalculationService.generateFeeReport(since);
    assertEquals(new BigDecimal("150.00"), total);
  }
}

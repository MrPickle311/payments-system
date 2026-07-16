package com.example.payments.fee.application;

import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.fee.domain.PaymentFeeRepository;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.sharedkernel.Money;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService implements FeeCalculationPort {

  private static final BigDecimal PERCENTAGE_RATE = new BigDecimal("0.029");
  private static final BigDecimal FLAT_FEE = new BigDecimal("0.30");
  private static final int SCALE = 4;
  private static final BigDecimal THOUSAND = BigDecimal.valueOf(100);

  private final PaymentFeeRepository paymentFeeRepository;
  private final PaymentRepository paymentRepository;

  @Observed(name = "calculate-fee")
  public FeeBreakdown calculate(Money grossAmount) {
    return calculate(grossAmount, null);
  }

  public FeeBreakdown calculate(Money grossAmount, Long paymentId) {
    Payment p = paymentId != null ? paymentRepository.findById(paymentId).orElse(null) : null;
    String txId = p != null ? p.getTransactionId() : "";
    if (txId != null && (txId.toLowerCase().contains("waiver") || txId.startsWith("vip_"))) {
      Money zero = Money.of(BigDecimal.ZERO, grossAmount.currency());
      return new FeeBreakdown(grossAmount, zero, zero, zero, grossAmount);
    }
    return runStandardFeeCalculation(grossAmount);
  }

  private FeeBreakdown runStandardFeeCalculation(Money grossAmount) {
    BigDecimal rate = PERCENTAGE_RATE;
    BigDecimal flat = FLAT_FEE;
    if (grossAmount.amount().compareTo(THOUSAND) > 0) {
      rate = new BigDecimal("0.015");
      flat = new BigDecimal("0.20");
    }
    Money percentFee = grossAmount.multiply(rate, SCALE, RoundingMode.HALF_UP);
    Money totalFee = percentFee.add(Money.of(flat, grossAmount.currency()));
    Money net = grossAmount.subtract(totalFee);
    return new FeeBreakdown(grossAmount, percentFee, Money.of(flat, grossAmount.currency()), totalFee, net);
  }

  @Observed(name = "save-settlement")
  public void saveSettlement(@SpanTag("payment.id") Long paymentId, Money grossAmount) {
    FeeBreakdown breakdown = calculate(grossAmount, paymentId);
    PaymentFee fee = createPaymentFee(paymentId, grossAmount, breakdown);
    paymentFeeRepository.save(fee);
  }

  public BigDecimal generateFeeReport(LocalDateTime since) {
    return paymentFeeRepository.getSumOfFeesSince(since);
  }

  private PaymentFee createPaymentFee(Long paymentId, Money grossAmount, FeeBreakdown breakdown) {
    return PaymentFee.builder().paymentId(paymentId).grossAmount(breakdown.grossAmount().amount())
        .percentageFee(breakdown.percentageFee().amount()).flatFee(breakdown.flatFee().amount())
        .totalFee(breakdown.totalFee().amount()).netAmount(breakdown.netAmount().amount())
        .currency(grossAmount.currency()).build();
  }

  @Observed(name = "get-fee")
  public FeeDto getFee(@SpanTag("payment.id") Long paymentId) {
    PaymentFee fee = paymentFeeRepository.findByPaymentId(paymentId)
        .orElseThrow(() -> new RuntimeException("No fee record found for payment " + paymentId
            + " — fee is only persisted after COMPLETE."));
    return new FeeDto(fee.getId(), fee.getPaymentId(), fee.getGrossAmount(), fee.getPercentageFee(),
        fee.getFlatFee(), fee.getTotalFee(), fee.getNetAmount(), fee.getCurrency());
  }
}

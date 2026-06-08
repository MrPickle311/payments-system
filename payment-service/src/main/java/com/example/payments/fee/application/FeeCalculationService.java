package com.example.payments.fee.application;

import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.fee.domain.PaymentFeeRepository;
import com.example.payments.payment.domain.Money;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
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
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final PaymentFeeRepository paymentFeeRepository;

  @Observed(name = "calculate-fee")
  public FeeBreakdown calculate(Money grossAmount) {
    Money percentageFee = grossAmount.multiply(PERCENTAGE_RATE, SCALE, RoundingMode.HALF_UP);
    Money totalFee = percentageFee.add(Money.of(FLAT_FEE, grossAmount.currency()));
    Money netAmount = grossAmount.subtract(totalFee);

    return new FeeBreakdown(grossAmount, percentageFee, Money.of(FLAT_FEE, grossAmount.currency()),
        totalFee, netAmount);
  }


  @Observed(name = "save-settlement")
  public void saveSettlement(@SpanTag("payment.id") Long paymentId, Money grossAmount) {
    FeeBreakdown breakdown = calculate(grossAmount);

    log.info("[FeeCalc] Settlement for payment {} | gross={} fee={} ({} % + {} flat) net={} {}",
        paymentId, breakdown.grossAmount().amount(), breakdown.totalFee().amount(),
        PERCENTAGE_RATE.multiply(HUNDRED).stripTrailingZeros().toPlainString(), FLAT_FEE,
        breakdown.netAmount().amount(), grossAmount.currency());

    PaymentFee fee = createPaymentFee(paymentId, grossAmount, breakdown);
    paymentFeeRepository.save(fee);
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

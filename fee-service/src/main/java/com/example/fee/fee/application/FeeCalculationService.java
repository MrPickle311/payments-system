package com.example.fee.fee.application;

import com.example.fee.fee.domain.FeeBreakdown;
import com.example.payments.common.sharedkernel.Money;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

  private static final BigDecimal PERCENTAGE_RATE = new BigDecimal("0.029");
  private static final BigDecimal FLAT_FEE = new BigDecimal("0.30");
  private static final int SCALE = 4;

  @Observed(name = "calculate-fee")
  public FeeBreakdown calculate(Money grossAmount) {
    Money percentageFee = grossAmount.multiply(PERCENTAGE_RATE, SCALE, RoundingMode.HALF_UP);
    Money totalFee = percentageFee.add(Money.of(FLAT_FEE, grossAmount.currency()));

    return new FeeBreakdown(totalFee);
  }
}

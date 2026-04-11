package com.example.payments.fee.application;

import com.example.payments.fee.domain.FeeBreakdown;
import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.fee.domain.PaymentFeeRepository;
import com.example.payments.common.domain.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Persists the fee record to the database.
 * Called during the COMPLETE transition (AUTHORIZED → COMPLETED) so the record
 * is written atomically with the payment state change.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

    private static final BigDecimal PERCENTAGE_RATE = new BigDecimal("0.029");
    private static final BigDecimal FLAT_FEE        = new BigDecimal("0.30");
    private static final int        SCALE            = 4;

    private final PaymentFeeRepository paymentFeeRepository;

    public FeeBreakdown calculate(Money grossAmount) {
        Money percentageFee = grossAmount.multiply(PERCENTAGE_RATE, SCALE, RoundingMode.HALF_UP);
        Money totalFee = percentageFee.add(Money.of(FLAT_FEE, grossAmount.getCurrency()));
        Money netAmount = grossAmount.subtract(totalFee);
        
        return new FeeBreakdown(grossAmount, percentageFee, Money.of(FLAT_FEE, grossAmount.getCurrency()), totalFee, netAmount);
    }

    /**
     * Persists the fee record to the database.
     */
    public PaymentFee saveSettlement(Long paymentId, Money grossAmount) {
        FeeBreakdown b = calculate(grossAmount);

        log.info("[FeeCalc] Settlement for payment {} | gross={} fee={} ({} % + {} flat) net={} {}",
                paymentId,
                b.grossAmount().getAmount(),
                b.totalFee().getAmount(),
                PERCENTAGE_RATE.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString(),
                FLAT_FEE,
                b.netAmount().getAmount(),
                grossAmount.getCurrency());

        PaymentFee fee = PaymentFee.builder()
                .paymentId(paymentId)
                .grossAmount(b.grossAmount().getAmount())
                .percentageFee(b.percentageFee().getAmount())
                .flatFee(b.flatFee().getAmount())
                .totalFee(b.totalFee().getAmount())
                .netAmount(b.netAmount().getAmount())
                .currency(grossAmount.getCurrency())
                .build();

        return paymentFeeRepository.save(fee);
    }

    public PaymentFee getFee(Long paymentId) {
        return paymentFeeRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new RuntimeException(
                        "No fee record found for payment " + paymentId
                                + " — fee is only persisted after COMPLETE."));
    }
}

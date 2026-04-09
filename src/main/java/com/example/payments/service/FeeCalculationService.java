package com.example.payments.service;

import com.example.payments.entity.PaymentFee;
import com.example.payments.repository.PaymentFeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates and persists payment-processing fees using Stripe-like pricing:
 * <pre>  fee = (gross × 2.9%) + $0.30</pre>
 *
 * <h2>Integration points in the state machine</h2>
 * <ol>
 *   <li>{@code feeCalculationAction} (PENDING → AUTHORIZED) calls {@link #calculate}
 *       and stores the breakdown in extended state — no DB write yet.</li>
 *   <li>{@code settlementAction} (AUTHORIZED → COMPLETED) calls {@link #saveSettlement}
 *       which persists the {@link PaymentFee} record inside the same transaction.</li>
 * </ol>
 *
 * Splitting calculation from persistence means the fee is always computed before the
 * payment is authorised (and can be displayed to the merchant), but only persisted
 * when the funds are actually captured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCalculationService {

    private static final BigDecimal PERCENTAGE_RATE = new BigDecimal("0.029");
    private static final BigDecimal FLAT_FEE        = new BigDecimal("0.30");
    private static final int        SCALE            = 4;

    private final PaymentFeeRepository paymentFeeRepository;

    /**
     * Pure calculation — no side effects, no DB access.
     * Called during the AUTHORIZE transition so the fee is known before capture.
     */
    public record FeeBreakdown(
            BigDecimal grossAmount,
            BigDecimal percentageFee,
            BigDecimal flatFee,
            BigDecimal totalFee,
            BigDecimal netAmount
    ) {}

    public FeeBreakdown calculate(BigDecimal grossAmount) {
        BigDecimal percentageFee = grossAmount
                .multiply(PERCENTAGE_RATE)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalFee = percentageFee
                .add(FLAT_FEE)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount
                .subtract(totalFee)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return new FeeBreakdown(grossAmount, percentageFee, FLAT_FEE, totalFee, netAmount);
    }

    /**
     * Persists the fee record to the database.
     * Called during the COMPLETE transition (AUTHORIZED → COMPLETED) so the record
     * is written atomically with the payment state change.
     */
    public PaymentFee saveSettlement(Long paymentId, BigDecimal grossAmount, String currency) {
        FeeBreakdown b = calculate(grossAmount);

        log.info("[FeeCalc] Settlement for payment {} | gross={} fee={} ({} % + {} flat) net={} {}",
                paymentId,
                b.grossAmount(),
                b.totalFee(),
                PERCENTAGE_RATE.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString(),
                FLAT_FEE,
                b.netAmount(),
                currency);

        PaymentFee fee = PaymentFee.builder()
                .paymentId(paymentId)
                .grossAmount(b.grossAmount())
                .percentageFee(b.percentageFee())
                .flatFee(b.flatFee())
                .totalFee(b.totalFee())
                .netAmount(b.netAmount())
                .currency(currency)
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

package com.example.payments.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Simulates a call to an external fraud-detection API (Sift / Kount / Stripe Radar).
 *
 * <p>In production this would be an HTTP call via {@code RestClient} or {@code WebClient}.
 * Here we reproduce the essential characteristics: network latency, a risk score in
 * [0, 100], and a binary ALLOW / BLOCK recommendation.
 *
 * <p>This service is called from the {@code fraudCheckGuard} inside the state machine.
 * The guard stores the score in extended state so the audit trail and error messages
 * can reference it later.
 *
 * <h2>Demo scoring rules</h2>
 * <ul>
 *   <li>{@code amount > 5 000} → score 92 (HIGH — blocked)</li>
 *   <li>{@code amount > 1 000} → score 55 (MEDIUM — allowed)</li>
 *   <li>{@code amount > 500}  → score 25 (LOW — allowed)</li>
 *   <li>anything else         → score 10 (MINIMAL — allowed)</li>
 * </ul>
 */
@Slf4j
@Service
public class FraudCheckService {

    private static final int HIGH_RISK_THRESHOLD = 70;
    /** Simulated round-trip to a remote fraud API. */
    private static final long API_LATENCY_MS = 80;

    public record FraudResult(int score, String riskLevel, String recommendation) {
        public boolean isHighRisk() {
            return score >= HIGH_RISK_THRESHOLD;
        }
    }

    /**
     * Evaluates fraud risk for a payment before it is authorised.
     *
     * @param paymentId  internal payment identifier (sent to fraud API as correlation ID)
     * @param amount     transaction amount
     * @param currency   ISO 4217 currency code
     * @return the fraud assessment
     */
    public FraudResult evaluate(Long paymentId, BigDecimal amount, String currency) {
        log.info("[FraudAPI] → Sending check request | payment={} amount={} {}",
                paymentId, amount, currency);

        simulateNetworkLatency();

        int score = computeScore(amount);
        String riskLevel = score >= HIGH_RISK_THRESHOLD ? "HIGH"
                : score >= 40 ? "MEDIUM"
                : "LOW";
        String recommendation = score >= HIGH_RISK_THRESHOLD ? "BLOCK" : "ALLOW";

        log.info("[FraudAPI] ← Response received | payment={} score={} risk={} recommendation={}",
                paymentId, score, riskLevel, recommendation);

        return new FraudResult(score, riskLevel, recommendation);
    }

    // -------------------------------------------------------------------------

    private int computeScore(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000")) > 0) return 92;
        if (amount.compareTo(new BigDecimal("1000")) > 0) return 55;
        if (amount.compareTo(new BigDecimal("500"))  > 0) return 25;
        return 10;
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(API_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

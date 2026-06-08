package com.example.payments.fraud.application;

import com.example.payments.fraud.domain.FraudRecord;
import com.example.payments.fraud.domain.FraudRecordRepository;
import com.example.payments.sharedkernel.Money;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
@Slf4j
public class FraudCheckService implements FraudCheckPort {

  public static final int MEDIUM_RISK_THRESHOLD = 40;
  private static final int HIGH_RISK_THRESHOLD = 70;
  private static final long API_LATENCY_MS = 80;

  private static final String HIGH_RISK_LEVEL = "HIGH";
  private static final String MEDIUM_RISK_LEVEL = "MEDIUM";
  private static final String LOW_RISK_LEVEL = "LOW";

  private static final String BLOCK_RECOMMENDATION = "BLOCK";
  private static final String ALLOW_RECOMMENDATION = "ALLOW";

  private static final BigDecimal AMOUNT_THRESHOLD_HIGH = new BigDecimal("5000");
  private static final BigDecimal AMOUNT_THRESHOLD_MEDIUM = new BigDecimal("1000");
  private static final BigDecimal AMOUNT_THRESHOLD_LOW = new BigDecimal("500");

  private static final int SCORE_HIGH = 92;
  private static final int SCORE_MEDIUM = 55;
  private static final int SCORE_LOW = 25;
  private static final int SCORE_MINIMAL = 10;

  private final FraudRecordRepository fraudRecordRepository;



  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Observed(name = "evaluate-fraud")
  public FraudResult evaluate(@SpanTag("payment.id") Long paymentId, Money money) {
    log.info("[FraudAPI] → Sending check request | payment={} amount={} {}", paymentId,
        money.amount(), money.currency());
    simulateNetworkLatency();
    int score = computeScore(money.amount());
    String riskLevel = getRiskLevel(score);
    String recommendation =
        score >= HIGH_RISK_THRESHOLD ? BLOCK_RECOMMENDATION : ALLOW_RECOMMENDATION;
    log.info("[FraudAPI] ← Response received | payment={} score={} risk={} recommendation={}",
        paymentId, score, riskLevel, recommendation);
    saveFraudRecord(paymentId, score, riskLevel, recommendation);
    return new FraudResult(score, riskLevel, recommendation);
  }

  private void saveFraudRecord(Long paymentId, int score, String riskLevel, String recommendation) {
    FraudRecord fraudRecord = FraudRecord.builder().paymentId(paymentId).score(score)
        .riskLevel(riskLevel).recommendation(recommendation).build();
    fraudRecordRepository.save(fraudRecord);
  }

  private static String getRiskLevel(int score) {
    if (score >= HIGH_RISK_THRESHOLD) {
      return HIGH_RISK_LEVEL;
    }

    if (score >= MEDIUM_RISK_THRESHOLD) {
      return MEDIUM_RISK_LEVEL;
    }

    return LOW_RISK_LEVEL;
  }

  private int computeScore(BigDecimal amount) {
    if (amount.compareTo(AMOUNT_THRESHOLD_HIGH) > 0) {
      return SCORE_HIGH;
    }
    if (amount.compareTo(AMOUNT_THRESHOLD_MEDIUM) > 0) {
      return SCORE_MEDIUM;
    }
    if (amount.compareTo(AMOUNT_THRESHOLD_LOW) > 0) {
      return SCORE_LOW;
    }
    return SCORE_MINIMAL;
  }

  private void simulateNetworkLatency() {
    try {
      Thread.sleep(API_LATENCY_MS);
    } catch (InterruptedException e) {
      log.warn("Network latency simulation interrupted", e);
      Thread.currentThread().interrupt();
    }
  }
}

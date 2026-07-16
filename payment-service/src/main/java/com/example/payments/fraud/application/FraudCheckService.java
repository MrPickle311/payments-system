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

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;


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
  private final PaymentRepository paymentRepository;
  private final PayerProfileRepository payerProfileRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Observed(name = "evaluate-fraud")
  public FraudResult evaluate(@SpanTag("payment.id") Long paymentId, Money money) {
    simulateNetworkLatency();
    Payment p = paymentRepository.findById(paymentId).orElse(null);
    String txId = p != null ? p.getTransactionId() : "";
    if (isSanctioned(txId)) {
      return blockPayment(paymentId, "SANCTIONS_BLOCK");
    }
    String segment = resolveSegment(txId);
    if (isLimitExceeded(segment, money.amount())) {
      return blockPayment(paymentId, "LIMIT_EXCEEDED");
    }
    return runStandardFraudCheck(paymentId, money.amount());
  }

  private boolean isSanctioned(String txId) {
    return txId != null && txId.toLowerCase().contains("sanctioned");
  }

  private FraudResult blockPayment(Long paymentId, String reason) {
    saveFraudRecord(paymentId, 100, HIGH_RISK_LEVEL, reason);
    return new FraudResult(100, HIGH_RISK_LEVEL, reason);
  }

  private String resolveSegment(String txId) {
    if (txId != null) {
      if (txId.startsWith("basic_")) {
        return "BASIC";
      }
      if (txId.startsWith("premium_")) {
        return "PREMIUM";
      }
    }
    PayerProfile profile = payerProfileRepository.findById(1L)
        .orElseGet(() -> createDefaultProfile(1L));
    return profile.getSegment();
  }

  @Transactional
  public void autoApproveKyc(Long payerId) {
    PayerProfile profile = payerProfileRepository.findById(payerId)
        .orElseGet(() -> PayerProfile.builder().payerId(payerId).build());
    profile.setKycStatus("VERIFIED");
    profile.setSegment("STANDARD");
    payerProfileRepository.save(profile);
  }

  @Transactional
  public void onboardPayer(Long payerId) {
    if (!payerProfileRepository.existsById(payerId)) {
      PayerProfile profile = PayerProfile.builder()
          .payerId(payerId).segment("BASIC").kycStatus("PENDING").build();
      payerProfileRepository.save(profile);
    }
  }

  private PayerProfile createDefaultProfile(Long payerId) {
    PayerProfile p = PayerProfile.builder()
        .payerId(payerId).segment("BASIC").kycStatus("PENDING").build();
    return payerProfileRepository.save(p);
  }

  private boolean isSingleLimitExceeded(String segment, BigDecimal amount) {
    if ("BASIC".equals(segment) && amount.compareTo(new BigDecimal("500.00")) > 0) {
      return true;
    }
    if ("STANDARD".equals(segment) && amount.compareTo(new BigDecimal("5000.00")) > 0) {
      return true;
    }
    return false;
  }

  private boolean isAccumulatedLimitExceeded(String segment, BigDecimal amount) {
    if ("BASIC".equals(segment)) {
      BigDecimal monthly = paymentRepository.getSumOfCompletedPaymentsSince(LocalDateTime.now().minusDays(30));
      return monthly.add(amount).compareTo(new BigDecimal("2000.00")) > 0;
    }
    if ("STANDARD".equals(segment)) {
      BigDecimal monthly = paymentRepository.getSumOfCompletedPaymentsSince(LocalDateTime.now().minusDays(30));
      return monthly.add(amount).compareTo(new BigDecimal("50000.00")) > 0;
    }
    if ("PREMIUM".equals(segment)) {
      BigDecimal daily = paymentRepository.getSumOfCompletedPaymentsSince(LocalDateTime.now().minusDays(1));
      return daily.add(amount).compareTo(new BigDecimal("500000.00")) > 0;
    }
    return false;
  }

  private boolean isLimitExceeded(String segment, BigDecimal amount) {
    return isSingleLimitExceeded(segment, amount) || isAccumulatedLimitExceeded(segment, amount);
  }

  private FraudResult runStandardFraudCheck(Long paymentId, BigDecimal amount) {
    int score = computeScore(amount);
    String riskLevel = getRiskLevel(score);
    String recommendation = score >= HIGH_RISK_THRESHOLD ? BLOCK_RECOMMENDATION : ALLOW_RECOMMENDATION;
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


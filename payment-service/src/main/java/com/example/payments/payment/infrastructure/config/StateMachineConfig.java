package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.fraud.application.FraudCheckPort.FraudResult;
import com.example.payments.payment.domain.Money;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import org.springframework.context.annotation.Configuration;

import static com.example.payments.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payments.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payments.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payments.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payments.payment.domain.PaymentConstants.PROCESSING_FEE;

import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

  private final FraudCheckPort fraudCheckService;
  private final FeeCalculationPort feeCalculationService;
  private final WalletClient walletClient;
  private final LedgerPublisher ledgerPublisher;


  @Override
  protected Guard<PaymentState, PaymentEvent> fraudCheckGuard() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      Money money = Money.of(amount, currency);

      FraudResult result = fraudCheckService.evaluate(paymentId, money);
      context.getExtendedState().getVariables().put(FRAUD_SCORE, result.score());
      context.getExtendedState().getVariables().put(FRAUD_RISK, result.riskLevel());

      if (result.isHighRisk()) {
        log.warn("[Guard:Fraud] BLOCKED payment={} score={} risk={}", paymentId, result.score(),
            result.riskLevel());
        return false;
      }

      log.info("[Guard:Fraud] ALLOWED payment={} score={} risk={}", paymentId, result.score(),
          result.riskLevel());
      return true;
    };
  }


  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      LocalDateTime createdAt =
          context.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);

      if (createdAt == null) {
        log.warn("[Guard:Refund] createdAt missing for payment {} — allowing refund", paymentId);
        return true;
      }

      LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
      boolean inWindow = createdAt.isAfter(cutoff);

      if (!inWindow) {
        log.warn("[Guard:Refund] BLOCKED payment={} — created={} is outside 30-day window",
            paymentId, createdAt);
      } else {
        log.info("[Guard:Refund] ALLOWED payment={} — created={} is within refund window",
            paymentId, createdAt);
      }
      return inWindow;
    };
  }


  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      Money money = Money.of(amount, currency);

      FeeBreakdown breakdown = feeCalculationService.calculate(money);
      context.getExtendedState().getVariables().put(PROCESSING_FEE, breakdown.totalFee().amount());
      context.getExtendedState().getVariables().put(NET_AMOUNT, breakdown.netAmount().amount());

      log.info("[Action:FeeCalc] payment={} | gross={} | fee={} ({}% + {} flat) | net={}",
          paymentId, amount, breakdown.totalFee().amount(), "2.9", breakdown.flatFee().amount(),
          breakdown.netAmount().amount());
    };
  }


  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
    return context -> log.error("[Action:FeeCalc] ERROR during fee calculation for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : "unknown");
  }


  @Override
  protected Action<PaymentState, PaymentEvent> settlementAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);

      FeeBreakdown breakdown = feeCalculationService.calculate(Money.of(amount, currency));
      BigDecimal net = breakdown.netAmount().amount();
      feeCalculationService.saveSettlement(paymentId, Money.of(amount, currency));
      walletClient.debit(paymentId, net, currency);
      ledgerPublisher.publishEvent(paymentId, amount, net, currency);
      log.info("[Action:Settlement] → POST /internal/accounting | payment={} net={} {}", paymentId,
          net, currency);
      simulateInternalApiCall(50);
      log.info("[Action:Settlement] ← Accounting system acknowledged payment {}", paymentId);
    };
  }


  @Override
  protected Action<PaymentState, PaymentEvent> settlementErrorAction() {
    return context -> log.error("[Action:Settlement] ERROR during settlement for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : "unknown");
  }


  @Override
  protected Action<PaymentState, PaymentEvent> completedEntryAction() {
    return context -> {
      if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class))) {
        log.debug("[Entry:Completed] Skipped — restoring from DB");
        return;
      }

      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);

      log.info("[Entry:Completed] Payment {} has been captured!", paymentId);
    };
  }

  private void simulateInternalApiCall(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      log.warn("Internal API simulation interrupted", e);
      Thread.currentThread().interrupt();
    }
  }
}

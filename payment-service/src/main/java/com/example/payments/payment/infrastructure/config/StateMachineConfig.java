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

import org.springframework.statemachine.StateContext;
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

  private static final String UNKNOWN_ERROR = "unknown";
  private static final int DAYS_30 = 30;

  private final FraudCheckPort fraudCheckService;
  private final FeeCalculationPort feeCalculationService;
  private final WalletClient walletClient;
  private final LedgerPublisher ledgerPublisher;

  @Override
  protected Guard<PaymentState, PaymentEvent> fraudCheckGuard() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      FraudResult result = evaluateFraud(context, paymentId);
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

  private FraudResult evaluateFraud(StateContext<PaymentState, PaymentEvent> context,
      Long paymentId) {
    Money money = Money.of(context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class),
        context.getExtendedState().get(PAYMENT_CURRENCY, String.class));
    FraudResult result = fraudCheckService.evaluate(paymentId, money);
    context.getExtendedState().getVariables().put(FRAUD_SCORE, result.score());
    context.getExtendedState().getVariables().put(FRAUD_RISK, result.riskLevel());
    return result;
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      LocalDateTime createdAt =
          context.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);
      if (createdAt == null)
        return true;
      boolean inWindow = createdAt.isAfter(LocalDateTime.now().minusDays(DAYS_30));
      if (!inWindow)
        log.warn("[Guard:Refund] BLOCKED payment={} outside 30-day window", paymentId);
      else
        log.info("[Guard:Refund] ALLOWED payment={} inside refund window", paymentId);
      return inWindow;
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      FeeBreakdown breakdown = feeCalculationService.calculate(
          Money.of(amount, context.getExtendedState().get(PAYMENT_CURRENCY, String.class)));
      context.getExtendedState().getVariables().put(PROCESSING_FEE, breakdown.totalFee().amount());
      context.getExtendedState().getVariables().put(NET_AMOUNT, breakdown.netAmount().amount());
      log.info("[Action:FeeCalc] payment={} gross={} fee={} net={}", paymentId, amount,
          breakdown.totalFee().amount(), breakdown.netAmount().amount());
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
    return context -> log.error("[Action:FeeCalc] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> settlementAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      Money money = Money.of(amount, currency);
      BigDecimal net = feeCalculationService.calculate(money).netAmount().amount();
      feeCalculationService.saveSettlement(paymentId, money);
      walletClient.debit(paymentId, net, currency);
      ledgerPublisher.publishEvent(paymentId, amount, net, currency);
      log.info("[Action:Settlement] POST /accounting | payment={} net={} {}", paymentId, net,
          currency);
      simulateInternalApiCall(50);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> settlementErrorAction() {
    return context -> log.error("[Action:Settlement] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> completedEntryAction() {
    return context -> {
      if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class)))
        return;
      log.info("[Entry:Completed] Payment {} captured!",
          context.getExtendedState().get(PAYMENT_ID, Long.class));
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

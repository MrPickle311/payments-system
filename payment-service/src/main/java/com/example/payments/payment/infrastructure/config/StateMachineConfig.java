package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.fraud.application.FraudCheckPort.FraudResult;
import com.example.payments.sharedkernel.Money;
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
import java.time.ZoneId;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

  private static final String UNKNOWN_ERROR = "unknown";
  private static final int DAYS_30 = 30;
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String RISK_MEDIUM = "MEDIUM";
  private static final String RISK_PENDING_REVIEW = "PENDING_REVIEW";
  private static final String RISK_MANUAL_REVIEW = "MANUAL_REVIEW";
  private static final String RISK_LOW = "LOW";
  private static final String RISK_OK = "OK";
  private static final String RISK_HIGH = "HIGH";

  private final FraudCheckPort fraudCheckService;
  private final FeeCalculationPort feeCalculationService;
  private final WalletClient walletClient;
  private final LedgerPublisher ledgerPublisher;

  @Override
  protected Guard<PaymentState, PaymentEvent> requireReviewGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_MEDIUM.equalsIgnoreCase(riskLevel)
          || RISK_PENDING_REVIEW.equalsIgnoreCase(riskLevel)
          || RISK_MANUAL_REVIEW.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> riskCheckGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_LOW.equalsIgnoreCase(riskLevel) || STATUS_SUCCESS.equalsIgnoreCase(riskLevel)
          || RISK_OK.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> riskRejectedGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_HIGH.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return context -> {
      LocalDateTime createdAt =
          context.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);
      if (createdAt == null) {
        return true;
      }
      return createdAt.isAfter(LocalDateTime.now(ZoneId.systemDefault()).minusDays(DAYS_30));
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> evaluateRiskAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      FraudResult result = fraudCheckService.evaluate(paymentId, Money.of(amount, curr));
      context.getExtendedState().getVariables().put(FRAUD_SCORE, result.score());
      context.getExtendedState().getVariables().put(FRAUD_RISK, result.riskLevel());
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> evaluateRiskErrorAction() {
    return context -> log.error("[Action:EvaluateRisk] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationAction() {
    return context -> {
      Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      FeeBreakdown breakdown = feeCalculationService.calculate(Money.of(amount, curr));
      context.getExtendedState().getVariables().put(PROCESSING_FEE, breakdown.totalFee().amount());
      context.getExtendedState().getVariables().put(NET_AMOUNT, breakdown.netAmount().amount());
      walletClient.debit(pId, amount, curr);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
    return context -> log.error("[Action:FeeCalc] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> reserveFeeAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal fee = context.getExtendedState().get(PROCESSING_FEE, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      walletClient.debit(paymentId, fee, curr);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> reserveFeeErrorAction() {
    return context -> log.error("[Action:ReserveFee] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> postLedgerAction() {
    return context -> {
      Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal gross = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      BigDecimal net = context.getExtendedState().get(NET_AMOUNT, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      ledgerPublisher.publishEvent(pId, gross, net, curr);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> postLedgerErrorAction() {
    return context -> log.error("[Action:PostLedger] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> unreserveBaseAmountAction() {
    return context -> {
      Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      walletClient.debit(pId, amount.negate(), curr);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> unreserveBaseAmountErrorAction() {
    return context -> log.error("[Action:UnreserveBase] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> compensateSagaAction() {
    return context -> {
      Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      BigDecimal fee = context.getExtendedState().get(PROCESSING_FEE, BigDecimal.class);
      String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      walletClient.debit(paymentId, fee.negate(), currency);
      walletClient.debit(paymentId, amount.negate(), currency);
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> compensateSagaErrorAction() {
    return context -> log.error("[Action:CompensateSaga] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> settlementAction() {
    return context -> {
      Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
      BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
      String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
      feeCalculationService.saveSettlement(pId, Money.of(amount, curr));
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
      if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class))) {
        return;
      }
      log.info("[Entry:Completed] Payment {} captured!",
          context.getExtendedState().get(PAYMENT_ID, Long.class));
    };
  }

  private void simulateInternalApiCall(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}

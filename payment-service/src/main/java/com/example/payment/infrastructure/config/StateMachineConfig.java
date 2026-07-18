package com.example.payment.infrastructure.config;

import com.example.payment.application.saga.PaymentProcessingSaga;
import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.infrastructure.external.ledger.LedgerPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

  private static final String UNKNOWN_ERROR = "unknown";
  private static final int DAYS_30 = 30;

  private final LedgerPublisher ledgerPublisher;
  private final PaymentProcessingSaga paymentProcessingSaga;

  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return ctx -> {
      var proxy = SagaContextProxy.of(ctx);
      LocalDateTime createdAt = proxy.getPaymentCreatedAt();
      if (createdAt == null) {
        return true;
      }
      boolean inWindow =
          createdAt.isAfter(LocalDateTime.now(ZoneId.systemDefault()).minusDays(DAYS_30));
      logRefundGuardResult(proxy.getPaymentId(), inWindow);
      return inWindow;
    };
  }

  private void logRefundGuardResult(Long id, boolean allowed) {
    if (allowed) {
      log.info("[Guard:Refund] ALLOWED payment={} inside refund window", id);
    } else {
      log.warn("[Guard:Refund] BLOCKED payment={} outside 30-day window", id);
    }
  }

  @Override
  protected void executeSyncFxCallAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.syncFxCall(context);
  }

  @Override
  protected void executeAsyncAuthorizationAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.startAuthorization(context);
  }

  @Override
  protected void executeAsyncFraudCheckAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.startFraudCheck(context);
  }

  @Override
  protected void executeAsyncLimitsCheckAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.startLimitsCheck(context);
  }

  @Override
  protected void executeAsyncSanctionsCheckAction(
      StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.startSanctionsCheck(context);
  }

  @Override
  protected void executeAsyncFeeCalculationAction(
      StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.asyncFeeCalculationAction(context);
  }

  @Override
  protected void executeAsyncFeeChargeAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.asyncFeeChargeAction(context);
  }

  @Override
  protected void executeSettlementAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.settlementAction(context);
    var proxy = SagaContextProxy.of(context);
    BigDecimal amount = proxy.getPaymentAmountAsBigDecimal();
    BigDecimal net = proxy.getNetAmountAsBigDecimal();
    String currency = proxy.getTargetCurrency();
    if (currency == null) {
      currency = proxy.getPaymentCurrency();
    }
    ledgerPublisher.publishEvent(proxy.getPaymentId(), amount, net != null ? net : amount,
        currency);
  }

  @Override
  protected void executeSettlementErrorAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    String msg =
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR;
    log.error("[Action:Settlement] ERROR for payment={}: {}", proxy.getPaymentId(), msg);
  }

  @Override
  protected void executeCompensateFeeAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.compensateFeeAction(context);
  }

  @Override
  protected void executeCompletedEntryAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    if (Boolean.TRUE.equals(proxy.getIsRestoring())) {
      return;
    }
    paymentProcessingSaga.completedEntry(context);
  }

  @Override
  protected void executeFailedEntryAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.failedEntry(context);
  }

  @Override
  protected void executeCanceledEntryAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.canceledEntry(context);
  }

  @Override
  protected void executeRefundedEntryAction(StateContext<PaymentState, PaymentEvent> context) {
    paymentProcessingSaga.refundedEntry(context);
  }
}

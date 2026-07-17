package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.payment.application.saga.PaymentProcessingSaga;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import com.example.payments.sharedkernel.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.example.payments.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payments.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payments.payment.domain.PaymentConstants.PROCESSING_FEE;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

  private static final String UNKNOWN_ERROR = "unknown";
  private static final int DAYS_30 = 30;

  private final FeeCalculationPort feeCalculationService;
  private final WalletClient walletClient;
  private final LedgerPublisher ledgerPublisher;
  private final PaymentProcessingSaga paymentProcessingSaga;

  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return ctx -> {
      Long id = ctx.getExtendedState().get(PAYMENT_ID, Long.class);
      LocalDateTime createdAt = ctx.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);
      if (createdAt == null)
        return true;
      boolean inWindow =
          createdAt.isAfter(LocalDateTime.now(ZoneId.systemDefault()).minusDays(DAYS_30));
      if (!inWindow)
        log.warn("[Guard:Refund] BLOCKED payment={} outside 30-day window", id);
      else
        log.info("[Guard:Refund] ALLOWED payment={} inside refund window", id);
      return inWindow;
    };
  }

  @Override
  protected void executeFeeCalculationAction(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    FeeBreakdown breakdown = feeCalculationService.calculate(
        Money.of(amount, context.getExtendedState().get(PAYMENT_CURRENCY, String.class)));
    context.getExtendedState().getVariables().put(PROCESSING_FEE, breakdown.totalFee().amount());
    context.getExtendedState().getVariables().put(NET_AMOUNT, breakdown.netAmount().amount());
    log.info("[Action:FeeCalc] payment={} gross={} fee={} net={}", paymentId, amount,
        breakdown.totalFee().amount(), breakdown.netAmount().amount());
  }

  @Override
  protected void executeFeeCalculationErrorAction(
      StateContext<PaymentState, PaymentEvent> context) {
    log.error("[Action:FeeCalc] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected void executeSettlementAction(StateContext<PaymentState, PaymentEvent> context) {
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
  }

  @Override
  protected void executeSettlementErrorAction(StateContext<PaymentState, PaymentEvent> context) {
    log.error("[Action:Settlement] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
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
  protected void executeCompletedEntryAction(StateContext<PaymentState, PaymentEvent> context) {
    if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class))) {
      return;
    }
    log.info("[Entry:Completed] Payment {} captured!",
        context.getExtendedState().get(PAYMENT_ID, Long.class));
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

  private void simulateInternalApiCall(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      log.warn("Internal API simulation interrupted", e);
      Thread.currentThread().interrupt();
    }
  }
}

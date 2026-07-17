package com.example.payments.payment.application.saga;

import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.payment.application.saga.external.*;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.sharedkernel.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payments.payment.domain.enums.PaymentEvent.AUTHORIZE;
import static com.example.payments.payment.domain.enums.PaymentEvent.AUTH_FAIL;
import static com.example.payments.payment.domain.enums.PaymentEvent.FRAUD_ALERT;
import static com.example.payments.payment.domain.enums.PaymentEvent.FRAUD_CLEAR;
import static com.example.payments.payment.domain.enums.PaymentEvent.FX_FAIL;
import static com.example.payments.payment.domain.enums.PaymentEvent.FX_SUCCESS;
import static com.example.payments.payment.domain.enums.PaymentEvent.LIMITS_CLEAR;
import static com.example.payments.payment.domain.enums.PaymentEvent.LIMITS_REJECT;
import static com.example.payments.payment.domain.enums.PaymentEvent.SANCTIONS_FAIL;
import static com.example.payments.payment.domain.enums.PaymentEvent.SANCTIONS_PASS;
import static com.example.payments.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payments.payment.domain.enums.PaymentState.LIMITS_OK;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingSaga {

  private final FraudCheckPort fraudCheckService;
  private final AuthorizationService authorizationService;
  private final LimitsService limitsService;
  private final SanctionsService sanctionsService;
  private final WebhookService webhookService;
  private final FxService fxService;

  private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public void syncFxCall(StateContext<PaymentState, PaymentEvent> context) {
    Long id = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Sync FX Call for paymentId={}", id);
    try {
      boolean success = fxService.processFx(id);
      PaymentEvent event = success ? FX_SUCCESS : FX_FAIL;
      context.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, id).build());
    } catch (Exception ex) {
      log.error("Error on syncFxCall for paymentId={}", id, ex);
      context.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(FX_FAIL).setHeader(PAYMENT_ID, id).build());
    }
  }

  public void startFraudCheck(StateContext<PaymentState, PaymentEvent> ctx) {
    Long id = ctx.getExtendedState().get(PAYMENT_ID, Long.class);
    Money m = Money.of(ctx.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class),
        ctx.getExtendedState().get(PAYMENT_CURRENCY, String.class));
    try {
      PaymentEvent evt = fraudCheckService.evaluate(id, m).isHighRisk() ? FRAUD_ALERT : FRAUD_CLEAR;
      ctx.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(evt).setHeader(PAYMENT_ID, id).build());
    } catch (Exception ex) {
      log.error("Error on startFraudCheck for paymentId={}", id, ex);
      ctx.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(FRAUD_ALERT).setHeader(PAYMENT_ID, id).build());
    }
  }

  public void startAuthorization(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Starting sync Authorization inside parallel region for paymentId={}",
        paymentId);
    try {
      var success = authorizationService.authorize(paymentId);
      PaymentEvent event = success ? AUTHORIZE : AUTH_FAIL;
      context.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, paymentId).build());
    } catch (Exception ex) {
      log.error("Error on startAuthorization for paymentId={}", paymentId, ex);
      context.getStateMachine().sendEvent(
          MessageBuilder.withPayload(AUTH_FAIL).setHeader(PAYMENT_ID, paymentId).build());
    }
  }

  public void startLimitsCheck(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Starting sync LimitsCheck for paymentId={}", paymentId);
    try {
      var success = limitsService.checkAndReserveLimits(paymentId);
      PaymentEvent event = success ? LIMITS_CLEAR : LIMITS_REJECT;
      context.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, paymentId).build());
    } catch (Exception ex) {
      log.error("Error on startLimitsCheck for paymentId={}", paymentId, ex);
      context.getStateMachine().sendEvent(
          MessageBuilder.withPayload(LIMITS_REJECT).setHeader(PAYMENT_ID, paymentId).build());
    }
  }

  public void startSanctionsCheck(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Starting sync SanctionsCheck for paymentId={}", paymentId);
    try {
      var success = sanctionsService.checkSanctions(paymentId);
      PaymentEvent event = success ? SANCTIONS_PASS : SANCTIONS_FAIL;
      context.getStateMachine()
          .sendEvent(MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, paymentId).build());
    } catch (Exception ex) {
      log.error("Error on startSanctionsCheck for paymentId={}", paymentId, ex);
      context.getStateMachine().sendEvent(
          MessageBuilder.withPayload(SANCTIONS_FAIL).setHeader(PAYMENT_ID, paymentId).build());
    }
  }

  public void executeCompensations(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    StateMachine<PaymentState, PaymentEvent> stateMachine = context.getStateMachine();
    Collection<State<PaymentState, PaymentEvent>> regions = stateMachine.getState().getStates();

    log.info("[Saga] Executing compensations for paymentId={}", paymentId);

    if (regions != null) {
      compensateAuth(regions, paymentId);
      compensateLimits(regions, paymentId);
    }
  }

  private void compensateAuth(Collection<State<PaymentState, PaymentEvent>> regions,
      Long paymentId) {
    if (isAuthCompensationNeeded(regions)) {
      log.info("[Saga] Reversing authorization for paymentId={}", paymentId);
      CompletableFuture.runAsync(() -> authorizationService.reverseAuthorization(paymentId),
          virtualThreadExecutor);
    }
  }

  private static boolean isAuthCompensationNeeded(Collection<State<PaymentState, PaymentEvent>> regions) {
    return regions.stream().anyMatch(s -> s.getId() == AUTH_APPROVED);
  }

  private void compensateLimits(Collection<State<PaymentState, PaymentEvent>> regions,
      Long paymentId) {
    if (regions.stream().anyMatch(s -> s.getId() == LIMITS_OK)) {
      log.info("[Saga] Releasing limits for paymentId={}", paymentId);
      CompletableFuture.runAsync(() -> limitsService.releaseLimit(paymentId),
          virtualThreadExecutor);
    }
  }

  public void completedEntry(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Payment {} completed successfully", paymentId);
    CompletableFuture.runAsync(() -> webhookService.sendWebhook(paymentId, "COMPLETED"),
        virtualThreadExecutor);
  }

  public void failedEntry(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.error("[Saga] Payment {} failed", paymentId);
    executeCompensations(context);
    CompletableFuture.runAsync(() -> webhookService.sendWebhook(paymentId, "FAILED"),
        virtualThreadExecutor);
  }

  public void canceledEntry(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Payment {} canceled", paymentId);
    CompletableFuture.runAsync(() -> webhookService.sendWebhook(paymentId, "CANCELED"),
        virtualThreadExecutor);
  }

  public void refundedEntry(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    log.info("[Saga] Payment {} refunded", paymentId);
    CompletableFuture.runAsync(() -> webhookService.sendWebhook(paymentId, "REFUNDED"),
        virtualThreadExecutor);
  }
}

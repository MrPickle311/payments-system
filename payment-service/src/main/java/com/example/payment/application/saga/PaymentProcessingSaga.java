package com.example.payment.application.saga;

import com.example.payment.application.service.WebhookService;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.gateway.AuthorizationGateway;
import com.example.payment.domain.gateway.FeeGateway;
import com.example.payment.domain.gateway.FraudGateway;
import com.example.payment.domain.gateway.FxGateway;
import com.example.payment.domain.gateway.LimitsGateway;
import com.example.payment.domain.gateway.SanctionsGateway;
import com.example.payment.domain.gateway.WalletDebitCommand;
import com.example.payment.domain.gateway.WalletGateway;
import com.example.payments.fee.grpc.FeeResponse;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.sanctions.grpc.SanctionsResponse;
import com.example.payments.wallet.grpc.DebitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

import static com.example.payment.domain.PaymentConstants.INTERNAL_FEE_USER_ID;
import static com.example.payment.domain.PaymentConstants.STATUS_AUTH_APPROVED;
import static com.example.payment.domain.PaymentConstants.STATUS_AUTH_REJECTED;
import static com.example.payment.domain.PaymentConstants.STATUS_FEE_CALCULATED;
import static com.example.payment.domain.PaymentConstants.STATUS_FEE_CHARGED;
import static com.example.payment.domain.PaymentConstants.STATUS_FEE_FAILED;
import static com.example.payment.domain.PaymentConstants.STATUS_FEE_REFUNDED;
import static com.example.payment.domain.PaymentConstants.STATUS_FRAUD_DETECTED;
import static com.example.payment.domain.PaymentConstants.STATUS_FRAUD_PASSED;
import static com.example.payment.domain.PaymentConstants.STATUS_LIMITS_EXCEEDED;
import static com.example.payment.domain.PaymentConstants.STATUS_LIMITS_OK;
import static com.example.payment.domain.PaymentConstants.STATUS_LIMITS_RELEASED;
import static com.example.payment.domain.PaymentConstants.STATUS_SANCTIONS_CLEARED;
import static com.example.payment.domain.PaymentConstants.STATUS_SANCTIONS_HIT;
import static com.example.payment.domain.enums.PaymentEvent.AUTH_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.AUTH_SUCCESS;
import static com.example.payment.domain.enums.PaymentEvent.FEE_CALC_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.FEE_CALC_SUCCESS;
import static com.example.payment.domain.enums.PaymentEvent.FEE_CHARGE_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.FEE_CHARGE_SUCCESS;
import static com.example.payment.domain.enums.PaymentEvent.FRAUD_ALERT;
import static com.example.payment.domain.enums.PaymentEvent.FRAUD_CLEAR;
import static com.example.payment.domain.enums.PaymentEvent.FX_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.FX_SUCCESS;
import static com.example.payment.domain.enums.PaymentEvent.LIMITS_CLEAR;
import static com.example.payment.domain.enums.PaymentEvent.LIMITS_REJECT;
import static com.example.payment.domain.enums.PaymentEvent.SANCTIONS_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SANCTIONS_PASS;
import static com.example.payment.domain.enums.PaymentState.CANCELED;
import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FAILED;
import static com.example.payment.domain.enums.PaymentState.REFUNDED;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingSaga {

  private final FraudGateway fraudGateway;
  private final AuthorizationGateway authorizationGateway;
  private final LimitsGateway limitsGateway;
  private final SanctionsGateway sanctionsGateway;
  private final FxGateway fxGateway;
  private final FeeGateway feeGateway;
  private final WalletGateway walletGateway;
  private final WebhookService webhookService;

  public void syncFxCall(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var response = callFxService(proxy);
      handleFxResponse(proxy, response);
    } catch (Exception ex) {
      log.error("Error on syncFxCall for paymentId={}", proxy.getPaymentId(), ex);
      proxy.sendEvent(FX_FAIL);
    }
  }

  private FxResponse callFxService(SagaContextProxy proxy) {
    String amount = proxy.getPaymentAmount();
    String srcCur = proxy.getSourceCurrency();
    String tgtCur = proxy.getTargetCurrency();
    if (srcCur == null) {
      srcCur = proxy.getPaymentCurrency();
    }
    return fxGateway.processFx(proxy.getPaymentId(), amount, srcCur, tgtCur);
  }

  private void handleFxResponse(SagaContextProxy proxy, FxResponse response) {
    if (response.getSuccess()) {
      proxy.setPaymentAmount(response.getConvertedAmount());
      proxy.sendEvent(FX_SUCCESS);
    } else {
      proxy.sendEvent(FX_FAIL);
    }
  }

  public void startFraudCheck(StateContext<PaymentState, PaymentEvent> ctx) {
    var proxy = SagaContextProxy.of(ctx);
    try {
      var res = callFraudService(proxy);
      proxy.setFraudStatus(res.getSuccess() ? STATUS_FRAUD_PASSED : STATUS_FRAUD_DETECTED);
      proxy.sendEvent(res.getSuccess() ? FRAUD_CLEAR : FRAUD_ALERT);
    } catch (Exception ex) {
      log.error("Error on startFraudCheck for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setFraudStatus(STATUS_FRAUD_DETECTED);
      proxy.sendEvent(FRAUD_ALERT);
    }
  }

  private FraudResponse callFraudService(SagaContextProxy proxy) {
    return fraudGateway.checkFraud(proxy.getPaymentId(), proxy.getPaymentAmount(),
        proxy.getPaymentCurrency());
  }

  public void startAuthorization(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = authorizationGateway.authorize(proxy.getPaymentId());
      proxy.setAuthStatus(res.getSuccess() ? STATUS_AUTH_APPROVED : STATUS_AUTH_REJECTED);
      proxy.sendEvent(res.getSuccess() ? AUTH_SUCCESS : AUTH_FAIL);
    } catch (Exception ex) {
      log.error("Error on startAuthorization for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setAuthStatus(STATUS_AUTH_REJECTED);
      proxy.sendEvent(AUTH_FAIL);
    }
  }

  public void startLimitsCheck(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = callLimitsService(proxy);
      proxy.setLimitsStatus(res.getSuccess() ? STATUS_LIMITS_OK : STATUS_LIMITS_EXCEEDED);
      proxy.sendEvent(res.getSuccess() ? LIMITS_CLEAR : LIMITS_REJECT);
    } catch (Exception ex) {
      log.error("Error on startLimitsCheck for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setLimitsStatus(STATUS_LIMITS_EXCEEDED);
      proxy.sendEvent(LIMITS_REJECT);
    }
  }

  private LimitsResponse callLimitsService(SagaContextProxy proxy) {
    return limitsGateway.checkLimits(proxy.getPaymentId(), proxy.getSourceUserId(),
        proxy.getPaymentAmount(), proxy.getPaymentCurrency());
  }

  public void startSanctionsCheck(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = callSanctionsService(proxy);
      proxy.setSanctionsStatus(res.getSuccess() ? STATUS_SANCTIONS_CLEARED : STATUS_SANCTIONS_HIT);
      proxy.sendEvent(res.getSuccess() ? SANCTIONS_PASS : SANCTIONS_FAIL);
    } catch (Exception ex) {
      log.error("Error on startSanctionsCheck for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setSanctionsStatus(STATUS_SANCTIONS_HIT);
      proxy.sendEvent(SANCTIONS_FAIL);
    }
  }

  private SanctionsResponse callSanctionsService(SagaContextProxy proxy) {
    return sanctionsGateway.checkSanctions(proxy.getPaymentId(), proxy.getSourceUserId(),
        proxy.getTargetUserId());
  }

  public void asyncFeeCalculationAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = callFeeService(proxy);
      handleFeeResponse(proxy, res);
    } catch (Exception ex) {
      log.error("Error on startFeeCalculation for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setFeeStatus(STATUS_FEE_FAILED);
      proxy.sendEvent(FEE_CALC_FAIL);
    }
  }

  private FeeResponse callFeeService(SagaContextProxy proxy) {
    String amount = proxy.getPaymentAmount();
    String sourceCurrency = proxy.getSourceCurrency();
    if (sourceCurrency == null) {
      sourceCurrency = proxy.getPaymentCurrency();
    }
    return feeGateway.calculateFee(proxy.getPaymentId(), amount, sourceCurrency);
  }

  private void handleFeeResponse(SagaContextProxy proxy, FeeResponse res) {
    if (res.getSuccess()) {
      proxy.setFeeAmount(res.getFeeAmount());
      proxy.setFeeStatus(STATUS_FEE_CALCULATED);
      proxy.sendEvent(FEE_CALC_SUCCESS);
    } else {
      proxy.setFeeStatus(STATUS_FEE_FAILED);
      proxy.sendEvent(FEE_CALC_FAIL);
    }
  }

  public void asyncFeeChargeAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = callWalletServiceForFee(proxy);
      handleFeeChargeResponse(proxy, res);
    } catch (Exception ex) {
      log.error("Error on chargeFee for paymentId={}", proxy.getPaymentId(), ex);
      proxy.setFeeStatus(STATUS_FEE_FAILED);
      proxy.sendEvent(FEE_CHARGE_FAIL);
    }
  }

  private DebitResponse callWalletServiceForFee(SagaContextProxy proxy) {
    String feeAmount = proxy.getFeeAmount();
    String sourceCurrency = proxy.getSourceCurrency();
    if (sourceCurrency == null) {
      sourceCurrency = proxy.getPaymentCurrency();
    }
    return walletGateway.debit(new WalletDebitCommand(proxy.getPaymentId(), proxy.getSourceUserId(),
        INTERNAL_FEE_USER_ID, feeAmount, sourceCurrency));
  }

  private void handleFeeChargeResponse(SagaContextProxy proxy, DebitResponse res) {
    if ("SUCCESS".equals(res.getStatus())) {
      proxy.setFeeStatus(STATUS_FEE_CHARGED);
      proxy.sendEvent(FEE_CHARGE_SUCCESS);
    } else {
      proxy.setFeeStatus(STATUS_FEE_FAILED);
      proxy.sendEvent(FEE_CHARGE_FAIL);
    }
  }

  public void compensateFeeAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    String feeAmount = proxy.getFeeAmount();
    Long sourceUserId = proxy.getSourceUserId();
    if (STATUS_FEE_CHARGED.equals(proxy.getFeeStatus()) && feeAmount != null
        && sourceUserId != null) {
      refundFee(proxy, feeAmount, sourceUserId);
    }
    if (STATUS_LIMITS_OK.equals(proxy.getLimitsStatus()) && sourceUserId != null) {
      releaseLimits(proxy, sourceUserId);
    }
  }

  private void refundFee(SagaContextProxy proxy, String feeAmount, Long sourceUserId) {
    String sourceCurrency = proxy.getSourceCurrency();
    if (sourceCurrency == null) {
      sourceCurrency = proxy.getPaymentCurrency();
    }
    try {
      walletGateway.debit(new WalletDebitCommand(proxy.getPaymentId(), INTERNAL_FEE_USER_ID,
          sourceUserId, feeAmount, sourceCurrency));
      proxy.setFeeStatus(STATUS_FEE_REFUNDED);
    } catch (Exception ex) {
      log.error("Fee compensation failed for paymentId={}", proxy.getPaymentId(), ex);
    }
  }

  private void releaseLimits(SagaContextProxy proxy, Long sourceUserId) {
    try {
      limitsGateway.releaseLimit(proxy.getPaymentId(), sourceUserId, proxy.getPaymentAmount());
      proxy.setLimitsStatus(STATUS_LIMITS_RELEASED);
    } catch (Exception ex) {
      log.warn("Limits release failed for paymentId={}", proxy.getPaymentId(), ex);
    }
  }

  public void settlementAction(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = callWalletServiceForSettlement(proxy);
      log.info("[Saga] Settlement complete paymentId={} status={}", proxy.getPaymentId(),
          res.getStatus());
    } catch (Exception ex) {
      log.error("Settlement failed for paymentId={}", proxy.getPaymentId(), ex);
      throw ex;
    }
  }

  private DebitResponse callWalletServiceForSettlement(SagaContextProxy proxy) {
    String targetCurrency = proxy.getTargetCurrency();
    if (targetCurrency == null) {
      targetCurrency = proxy.getPaymentCurrency();
    }
    return walletGateway.debit(new WalletDebitCommand(proxy.getPaymentId(), proxy.getSourceUserId(),
        proxy.getTargetUserId(), proxy.getPaymentAmount(), targetCurrency));
  }

  public void completedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} COMPLETED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), COMPLETED.name());
  }

  public void failedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} FAILED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), FAILED.name());
  }

  public void canceledEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} CANCELED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), CANCELED.name());
  }

  public void refundedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} REFUNDED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), REFUNDED.name());
  }
}

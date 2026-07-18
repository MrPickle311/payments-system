package com.example.payment.application.saga;

import com.example.payment.application.service.WebhookService;
import com.example.payments.authorization.grpc.AuthorizationRequest;
import com.example.payments.authorization.grpc.AuthorizationServiceGrpc;
import com.example.payments.fee.grpc.FeeRequest;
import com.example.payments.fee.grpc.FeeServiceGrpc;
import com.example.payments.fraud.grpc.FraudRequest;
import com.example.payments.fraud.grpc.FraudServiceGrpc;
import com.example.payments.fx.grpc.FxRequest;
import com.example.payments.fx.grpc.FxServiceGrpc;
import com.example.payments.limits.grpc.LimitsRequest;
import com.example.payments.limits.grpc.LimitsServiceGrpc;
import com.example.payments.limits.grpc.ReleaseLimitRequest;
import com.example.payments.sanctions.grpc.SanctionsRequest;
import com.example.payments.sanctions.grpc.SanctionsServiceGrpc;
import com.example.payments.wallet.grpc.DebitRequest;
import com.example.payments.wallet.grpc.WalletServiceGrpc;
import com.example.payments.wallet.grpc.DebitResponse;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.sanctions.grpc.SanctionsResponse;
import com.example.payments.fee.grpc.FeeResponse;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
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
import static com.example.payment.domain.enums.PaymentEvent.AUTHORIZE;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingSaga {

  private static final String ZERO_AMOUNT = "0";
  private static final String EMPTY_STRING = "";

  @GrpcClient("fraud-service")
  private final FraudServiceGrpc.FraudServiceBlockingStub fraudCheckService;

  @GrpcClient("authorization-service")
  private final AuthorizationServiceGrpc.AuthorizationServiceBlockingStub authorizationService;

  @GrpcClient("limits-service")
  private final LimitsServiceGrpc.LimitsServiceBlockingStub limitsService;

  @GrpcClient("sanctions-service")
  private final SanctionsServiceGrpc.SanctionsServiceBlockingStub sanctionsService;

  @GrpcClient("fx-service")
  private final FxServiceGrpc.FxServiceBlockingStub fxService;

  @GrpcClient("fee-service")
  private final FeeServiceGrpc.FeeServiceBlockingStub feeService;

  @GrpcClient("wallet-service")
  private final WalletServiceGrpc.WalletServiceBlockingStub walletService;

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
    return fxService.processFx(FxRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setAmount(amount != null ? amount : ZERO_AMOUNT)
        .setSourceCurrency(srcCur != null ? srcCur : EMPTY_STRING)
        .setTargetCurrency(tgtCur != null ? tgtCur : srcCur).build());
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
    String amount = proxy.getPaymentAmount();
    String currency = proxy.getPaymentCurrency();
    return fraudCheckService.checkFraud(FraudRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setAmount(amount != null ? amount : ZERO_AMOUNT).setCurrency(currency != null ? currency : EMPTY_STRING)
        .build());
  }

  public void startAuthorization(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    try {
      var res = authorizationService
          .authorize(AuthorizationRequest.newBuilder().setPaymentId(proxy.getPaymentId()).build());
      proxy.setAuthStatus(res.getSuccess() ? STATUS_AUTH_APPROVED : STATUS_AUTH_REJECTED);
      proxy.sendEvent(res.getSuccess() ? AUTHORIZE : AUTH_FAIL);
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
    Long userId = proxy.getSourceUserId();
    String amount = proxy.getPaymentAmount();
    String currency = proxy.getPaymentCurrency();
    return limitsService.checkLimits(LimitsRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setSourceUserId(userId != null ? userId : 0L).setAmount(amount != null ? amount : ZERO_AMOUNT)
        .setCurrency(currency != null ? currency : EMPTY_STRING).build());
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
    Long sourceUserId = proxy.getSourceUserId();
    Long targetUserId = proxy.getTargetUserId();
    return sanctionsService.checkSanctions(SanctionsRequest.newBuilder()
        .setPaymentId(proxy.getPaymentId()).setSourceUserId(sourceUserId != null ? sourceUserId : 0L)
        .setTargetUserId(targetUserId != null ? targetUserId : 0L).build());
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
    return feeService.calculateFee(FeeRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setAmount(amount != null ? amount : ZERO_AMOUNT)
        .setCurrency(sourceCurrency != null ? sourceCurrency : EMPTY_STRING).build());
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
    Long sourceUserId = proxy.getSourceUserId();
    String feeAmount = proxy.getFeeAmount();
    String sourceCurrency = proxy.getSourceCurrency();
    if (sourceCurrency == null) {
      sourceCurrency = proxy.getPaymentCurrency();
    }
    return walletService.debit(DebitRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setSourceUserId(sourceUserId != null ? sourceUserId : 0L).setTargetUserId(INTERNAL_FEE_USER_ID)
        .setAmount(feeAmount != null ? feeAmount : ZERO_AMOUNT)
        .setCurrency(sourceCurrency != null ? sourceCurrency : EMPTY_STRING).build());
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
    if (STATUS_FEE_CHARGED.equals(proxy.getFeeStatus()) && feeAmount != null && sourceUserId != null) {
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
      var req = DebitRequest.newBuilder().setPaymentId(proxy.getPaymentId())
          .setSourceUserId(INTERNAL_FEE_USER_ID).setTargetUserId(sourceUserId).setAmount(feeAmount)
          .setCurrency(sourceCurrency != null ? sourceCurrency : EMPTY_STRING).build();
      walletService.debit(req);
      proxy.setFeeStatus(STATUS_FEE_REFUNDED);
    } catch (Exception ex) {
      log.error("Fee compensation failed for paymentId={}", proxy.getPaymentId(), ex);
    }
  }

  private void releaseLimits(SagaContextProxy proxy, Long sourceUserId) {
    String amount = proxy.getPaymentAmount();
    try {
      limitsService.releaseLimit(ReleaseLimitRequest.newBuilder().setPaymentId(proxy.getPaymentId())
          .setSourceUserId(sourceUserId).setAmount(amount != null ? amount : ZERO_AMOUNT).build());
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
    Long sourceUserId = proxy.getSourceUserId();
    Long targetUserId = proxy.getTargetUserId();
    String amount = proxy.getPaymentAmount();
    String targetCurrency = proxy.getTargetCurrency();
    if (targetCurrency == null) {
      targetCurrency = proxy.getPaymentCurrency();
    }
    var req = DebitRequest.newBuilder().setPaymentId(proxy.getPaymentId())
        .setSourceUserId(sourceUserId != null ? sourceUserId : 0L).setTargetUserId(targetUserId != null ? targetUserId : 0L)
        .setAmount(amount != null ? amount : ZERO_AMOUNT).setCurrency(targetCurrency != null ? targetCurrency : EMPTY_STRING)
        .build();
    return walletService.debit(req);
  }

  public void completedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} COMPLETED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), "COMPLETED");
  }

  public void failedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} FAILED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), "FAILED");
  }

  public void canceledEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} CANCELED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), "CANCELED");
  }

  public void refundedEntry(StateContext<PaymentState, PaymentEvent> context) {
    var proxy = SagaContextProxy.of(context);
    log.info("[Saga] Payment {} REFUNDED", proxy.getPaymentId());
    webhookService.sendWebhook(proxy.getPaymentId(), "REFUNDED");
  }
}

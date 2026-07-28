package com.example.payment.application.saga;

import com.example.payment.application.service.WebhookService;
import com.example.payment.domain.PaymentConstants;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.gateway.AuthorizationGateway;
import com.example.payment.domain.gateway.FeeGateway;
import com.example.payment.domain.gateway.FraudGateway;
import com.example.payment.domain.gateway.FxGateway;
import com.example.payment.domain.gateway.LimitsGateway;
import com.example.payment.domain.gateway.SanctionsGateway;
import com.example.payment.domain.gateway.WalletDebitCommand;
import com.example.payment.domain.gateway.WalletGateway;
import com.example.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.common.sharedkernel.outbox.OutboxEventEntity;
import com.example.payments.common.sharedkernel.outbox.OutboxRepositoryProxy;
import com.example.payments.fee.grpc.FeeResponse;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.wallet.grpc.DebitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FAILED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_FAILED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_FAILED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_FAILED;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingSaga {

    private static final String AGGREGATE_TYPE_PAYMENT = "Payment";
    private static final String TOPIC_COMPENSATIONS = "payment-compensations";
    
    private static final String SUFFIX_LIMITS_CHECK = "-LIMITS_CHECK";
    private static final String SUFFIX_FEE_CHARGE = "-FEE_CHARGE";
    private static final String SUFFIX_FEE_REFUND = "-FEE_REFUND";
    private static final String SUFFIX_LIMITS_RELEASE = "-LIMITS_RELEASE";
    private static final String SUFFIX_SETTLEMENT = "-SETTLEMENT";
    
    private static final String EVENT_FEE_REFUND_COMPENSATION = "FEE_REFUND_COMPENSATION";
    private static final String EVENT_LIMITS_RELEASE_COMPENSATION = "LIMITS_RELEASE_COMPENSATION";
    
    private static final String PAYLOAD_TEMPLATE_FEE_REFUND = "{\"paymentId\":%d,\"targetUserId\":%d,\"amount\":\"%s\",\"currency\":\"%s\"}";
    private static final String PAYLOAD_TEMPLATE_LIMITS_RELEASE = "{\"paymentId\":%d,\"sourceUserId\":%d,\"amount\":\"%s\"}";
    
    private static final String SUCCESS_STATUS = "SUCCESS";

    private final OutboxRepositoryProxy outboxRepositoryProxy;
    private final FraudGateway fraudGateway;
    private final AuthorizationGateway authorizationGateway;
    private final LimitsGateway limitsGateway;
    private final SanctionsGateway sanctionsGateway;
    private final FxGateway fxGateway;
    private final FeeGateway feeGateway;
    private final WalletGateway walletGateway;
    private final WebhookService webhookService;
    private final LedgerPublisher ledgerPublisher;

    public void syncFxCall(SagaContextProxy proxy) {
        try {
            var response = callFxService(proxy);
            handleFxResponse(proxy, response);
        } catch (Exception ex) {
            log.error("Error on syncFxCall for paymentId={}", proxy.getPaymentId(), ex);
            proxy.sendEvent(PaymentEvent.FX_FAIL);
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
            proxy.sendEvent(PaymentEvent.FX_SUCCESS);
        } else {
            proxy.sendEvent(PaymentEvent.FX_FAIL);
        }
    }

    public void startFraudCheck(SagaContextProxy proxy) {
        try {
            var res = callFraudService(proxy);
            proxy.setFraudStatus(res.getSuccess() ? PaymentConstants.STATUS_FRAUD_PASSED : PaymentConstants.STATUS_FRAUD_DETECTED);
            proxy.sendEvent(res.getSuccess() ? PaymentEvent.FRAUD_CLEAR : PaymentEvent.FRAUD_ALERT);
        } catch (Exception ex) {
            log.error("Error on startFraudCheck for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setFraudStatus(PaymentConstants.STATUS_FRAUD_FAILED);
            proxy.sendEvent(PaymentEvent.FRAUD_FAIL);
        }
    }

    private FraudResponse callFraudService(SagaContextProxy proxy) {
        return fraudGateway.checkFraud(proxy.getPaymentId(), proxy.getPaymentAmount(), proxy.getPaymentCurrency());
    }

    public void startAuthorization(SagaContextProxy proxy) {
        try {
            var response = authorizationGateway.authorize(proxy.getPaymentId());
            proxy.setAuthStatus(response.getSuccess() ? PaymentConstants.STATUS_AUTH_APPROVED : PaymentConstants.STATUS_AUTH_REJECTED);
            proxy.sendEvent(response.getSuccess() ? PaymentEvent.AUTH_SUCCESS : PaymentEvent.AUTH_REJECT);
        } catch (Exception ex) {
            log.error("Error on startAuthorization for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setAuthStatus(AUTH_REJECTED.name());
            proxy.sendEvent(PaymentEvent.AUTH_FAIL);
        }
    }

    public void startLimitsCheck(SagaContextProxy proxy) {
        try {
            var res = callLimitsService(proxy);
            proxy.setLimitsStatus(res.getSuccess() ? PaymentConstants.STATUS_LIMITS_OK : PaymentConstants.STATUS_LIMITS_EXCEEDED);
            proxy.sendEvent(res.getSuccess() ? PaymentEvent.LIMITS_CLEAR : PaymentEvent.LIMITS_REJECT);
        } catch (Exception ex) {
            log.error("Error on startLimitsCheck for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setLimitsStatus(LIMITS_FAILED.name());
            proxy.sendEvent(PaymentEvent.LIMITS_FAIL);
        }
    }

    private LimitsResponse callLimitsService(SagaContextProxy proxy) {
        String idempotencyKey = proxy.getPaymentId() + SUFFIX_LIMITS_CHECK;
        return limitsGateway.checkLimits(
                proxy.getPaymentId(), proxy.getSourceUserId(), proxy.getPaymentAmount(), proxy.getPaymentCurrency(), idempotencyKey);
    }

    public void startSanctionsCheck(SagaContextProxy proxy) {
        try {
            boolean isSanctioned = callSanctionsService(proxy);
            proxy.setSanctionsStatus(isSanctioned ? PaymentConstants.STATUS_SANCTIONS_CLEARED : PaymentConstants.STATUS_SANCTIONS_HIT);
            proxy.sendEvent(isSanctioned ? PaymentEvent.SANCTIONS_PASS : PaymentEvent.SANCTIONS_HIT);
        } catch (Exception ex) {
            log.error("Error on startSanctionsCheck for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setSanctionsStatus(SANCTIONS_FAILED.name());
            proxy.sendEvent(PaymentEvent.SANCTIONS_FAIL);
        }
    }

    private boolean callSanctionsService(SagaContextProxy proxy) {
        return sanctionsGateway.checkSanctions(proxy.getPaymentId(), proxy.getSourceUserId(), proxy.getTargetUserId());
    }

    public void feeCalculation(SagaContextProxy proxy) {
        try {
            var res = callFeeService(proxy);
            handleFeeResponse(proxy, res);
        } catch (Exception ex) {
            log.error("Error on startFeeCalculation for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setFeeStatus(FEE_FAILED.name());
            proxy.sendEvent(PaymentEvent.FEE_CALC_FAIL);
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
            proxy.setFeeStatus(PaymentEvent.FEE_CALC_SUCCESS.name());
            proxy.sendEvent(PaymentEvent.FEE_CALC_SUCCESS);
        } else {
            proxy.setFeeStatus(FEE_FAILED.name());
            proxy.sendEvent(PaymentEvent.FEE_CALC_FAIL);
        }
    }

    public void feeCharge(SagaContextProxy proxy) {
        try {
            var res = callWalletServiceForFee(proxy);
            handleFeeChargeResponse(proxy, res);
        } catch (Exception ex) {
            log.error("Error on chargeFee for paymentId={}", proxy.getPaymentId(), ex);
            proxy.setFeeStatus(FEE_FAILED.name());
            proxy.sendEvent(PaymentEvent.FEE_CHARGE_FAIL);
        }
    }

    private DebitResponse callWalletServiceForFee(SagaContextProxy proxy) {
        String feeAmount = proxy.getFeeAmount();
        String sourceCurrency = proxy.getSourceCurrency();
        if (sourceCurrency == null) {
            sourceCurrency = proxy.getPaymentCurrency();
        }
        return walletGateway.debit(WalletDebitCommand.builder()
                .paymentId(proxy.getPaymentId())
                .sourceUserId(proxy.getSourceUserId())
                .targetUserId(PaymentConstants.INTERNAL_FEE_USER_ID)
                .amount(feeAmount)
                .currency(sourceCurrency)
                .idempotencyKey(proxy.getPaymentId() + SUFFIX_FEE_CHARGE)
                .build());
    }

    private void handleFeeChargeResponse(SagaContextProxy proxy, DebitResponse res) {
        if (SUCCESS_STATUS.equals(res.getStatus())) {
            proxy.setFeeStatus(FEE_CHARGED.name());
            proxy.sendEvent(PaymentEvent.FEE_CHARGE_SUCCESS);
        } else {
            proxy.setFeeStatus(FEE_FAILED.name());
            proxy.sendEvent(PaymentEvent.FEE_CHARGE_FAIL);
        }
    }

    public void compensateFeeAction(SagaContextProxy proxy) {
        String feeAmount = proxy.getFeeAmount();
        Long sourceUserId = proxy.getSourceUserId();
        if (feeAmount != null && sourceUserId != null) {
            refundFee(proxy, feeAmount, sourceUserId);
        }
    }

    private void refundFee(SagaContextProxy proxy, String feeAmount, Long sourceUserId) {
        String currency = proxy.getSourceCurrency() != null ? proxy.getSourceCurrency() : proxy.getPaymentCurrency();
        try {
            walletGateway.debit(WalletDebitCommand.builder()
                    .paymentId(proxy.getPaymentId())
                    .sourceUserId(PaymentConstants.INTERNAL_FEE_USER_ID)
                    .targetUserId(sourceUserId)
                    .amount(feeAmount)
                    .currency(currency)
                    .idempotencyKey(proxy.getPaymentId() + SUFFIX_FEE_REFUND)
                    .build());
            proxy.setFeeStatus(PaymentConstants.STATUS_FEE_REFUNDED);
        } catch (Exception ex) {
            handleRefundFeeError(proxy, feeAmount, sourceUserId, ex);
        }
    }

    private void handleRefundFeeError(SagaContextProxy proxy, String fee, Long userId, Exception ex) {
        String currency = proxy.getSourceCurrency() != null ? proxy.getSourceCurrency() : proxy.getPaymentCurrency();
        log.error("Fee compensation failed for paymentId={}, saving to outbox for retry", proxy.getPaymentId(), ex);
        String payload = String.format(
                PAYLOAD_TEMPLATE_FEE_REFUND,
                proxy.getPaymentId(), userId, fee, currency);
        saveCompensationOutbox(proxy.getPaymentId(), EVENT_FEE_REFUND_COMPENSATION, payload);
    }

    public void compensateLimitsAction(SagaContextProxy proxy) {
        Long sourceUserId = proxy.getSourceUserId();
        if (sourceUserId != null) {
            releaseLimits(proxy, sourceUserId);
        }
    }

    private void releaseLimits(SagaContextProxy proxy, Long sourceUserId) {
        String idempotencyKey = proxy.getPaymentId() + SUFFIX_LIMITS_RELEASE;
        try {
            var res = limitsGateway.releaseLimit(proxy.getPaymentId(), sourceUserId, proxy.getPaymentAmount(), idempotencyKey);
            if (res.getReleased()) {
                proxy.setLimitsStatus(PaymentConstants.STATUS_LIMITS_RELEASED);
            } else {
                handleReleaseLimitsError(proxy, sourceUserId,
                        new IllegalStateException("releaseLimit non-success for paymentId=" + proxy.getPaymentId()));
            }
        } catch (Exception ex) {
            handleReleaseLimitsError(proxy, sourceUserId, ex);
        }
    }

    private void handleReleaseLimitsError(SagaContextProxy proxy, Long userId, Exception ex) {
        log.warn("Limits release failed for paymentId={}, saving to outbox for retry", proxy.getPaymentId(), ex);
        String payload = String.format(
                PAYLOAD_TEMPLATE_LIMITS_RELEASE,
                proxy.getPaymentId(), userId, proxy.getPaymentAmount());
        saveCompensationOutbox(proxy.getPaymentId(), EVENT_LIMITS_RELEASE_COMPENSATION, payload);
    }


    public void saveCompensationOutbox(Long paymentId, String eventType, String innerPayload) {
        if (paymentId == null) {
            log.error("[PaymentProcessingSaga] Cannot save compensation outbox event with null paymentId for eventType={}", eventType);
            return;
        }
        String wrappedPayload = String.format(
                "{\"eventType\":\"%s\",\"data\":%s}", eventType, innerPayload);
        outboxRepositoryProxy.save(OutboxEventEntity.builder()
                .aggregateId(String.valueOf(paymentId))
                .aggregateType(AGGREGATE_TYPE_PAYMENT)
                .eventType(eventType)
                .payload(wrappedPayload)
                .topic(TOPIC_COMPENSATIONS)
                .processed(false)
                .build());
    }


    public void settlementAction(SagaContextProxy proxy) {
        try {
            var response = callWalletServiceForSettlement(proxy);
            String status = response.getStatus();
            log.info("[Saga] Settlement wallet response paymentId={} status={}", proxy.getPaymentId(), status);
            if (SUCCESS_STATUS.equals(status)) {
                log.info("[Saga] Settlement complete paymentId={}", proxy.getPaymentId());
                proxy.sendEvent(PaymentEvent.WALLET_SETTLED_SUCCESS);
            } else {
                log.error("[Saga] Settlement rejected by wallet-service for paymentId={}, reason={}", proxy.getPaymentId(), status);
                proxy.sendEvent(PaymentEvent.WALLET_SETTLED_FAIL);
            }
        } catch (Exception ex) {
            log.error("Settlement failed for paymentId={}", proxy.getPaymentId(), ex);
            proxy.sendEvent(PaymentEvent.WALLET_SETTLED_FAIL);
        }
    }

    private DebitResponse callWalletServiceForSettlement(SagaContextProxy proxy) {
        String targetCurrency = proxy.getTargetCurrency();
        if (targetCurrency == null) {
            targetCurrency = proxy.getPaymentCurrency();
        }
        return walletGateway.debit(WalletDebitCommand.builder()
                .paymentId(proxy.getPaymentId())
                .sourceUserId(proxy.getSourceUserId())
                .targetUserId(proxy.getTargetUserId())
                .amount(proxy.getPaymentAmount())
                .currency(targetCurrency)
                .idempotencyKey(proxy.getPaymentId() + SUFFIX_SETTLEMENT)
                .build());
    }

    public void ledgerNotification(SagaContextProxy proxy) {
        BigDecimal amount = proxy.getPaymentAmountAsBigDecimal();
        BigDecimal net = proxy.getNetAmountAsBigDecimal();
        String currency = proxy.getTargetCurrency();
        if (currency == null) {
            currency = proxy.getPaymentCurrency();
        }
        try {
            ledgerPublisher.publishEvent(proxy.getPaymentId(), amount, net != null ? net : amount, currency);
            proxy.sendEvent(PaymentEvent.LEDGER_NOTIFY_SUCCESS);
        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("[Action:LedgerNotification] ERROR for payment={}: {}", proxy.getPaymentId(), msg);
            proxy.sendEvent(PaymentEvent.LEDGER_NOTIFY_FAIL);
        }
    }

    public void completedEntry(SagaContextProxy proxy) {
        log.info("[Saga] Payment {} COMPLETED", proxy.getPaymentId());
        webhookService.sendWebhook(proxy.getPaymentId(), COMPLETED.name());
    }

    public void failedEntry(SagaContextProxy proxy) {
        log.info("[Saga] Payment {} FAILED", proxy.getPaymentId());
        webhookService.sendWebhook(proxy.getPaymentId(), FAILED.name());
    }

    public boolean isFeeCharged(SagaContextProxy proxy) {
        return proxy.getFeeStatus() == FEE_CHARGED;
    }

    public boolean limitsOk(SagaContextProxy proxy) {
        return proxy.getLimitsStatus() == LIMITS_OK;
    }
}

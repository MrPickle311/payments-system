package com.example.payment.infrastructure.config;

import com.example.payment.application.saga.PaymentProcessingSaga;
import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

    private final PaymentProcessingSaga paymentProcessingSaga;
    private final ExecutorService virtualThreadExecutor;

    @Override
    protected ExecutorService virtualThreadExecutor() {
        return virtualThreadExecutor;
    }

    @Override
    protected void executeSyncFxCall(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.syncFxCall(SagaContextProxy.of(context));
    }

    @Override
    protected void executeAuthorization(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.startAuthorization(SagaContextProxy.of(context));
    }

    @Override
    protected void executeFraudCheck(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.startFraudCheck(SagaContextProxy.of(context));
    }

    @Override
    protected void executeLimitsCheck(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.startLimitsCheck(SagaContextProxy.of(context));
    }

    @Override
    protected void executeSanctionsCheck(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.startSanctionsCheck(SagaContextProxy.of(context));
    }

    @Override
    protected void executeFeeCalculation(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.feeCalculation(SagaContextProxy.of(context));
    }

    @Override
    protected void executeFeeCharge(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.feeCharge(SagaContextProxy.of(context));
    }

    @Override
    protected void executeWalletSettlement(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.settlementAction(SagaContextProxy.of(context));
    }

    @Override
    protected void executeLedgerNotification(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.ledgerNotification(SagaContextProxy.of(context));
    }

    @Override
    protected void executeCompensateFee(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.compensateFeeAction(SagaContextProxy.of(context));
    }

    @Override
    protected void executeCompensateLimits(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.compensateLimitsAction(SagaContextProxy.of(context));
    }

    @Override
    protected Guard<PaymentState, PaymentEvent> feeCharged() {
        return context -> paymentProcessingSaga.isFeeCharged(SagaContextProxy.of(context));
    }

    @Override
    protected Guard<PaymentState, PaymentEvent> limitsOk() {
        return context -> paymentProcessingSaga.limitsOk(SagaContextProxy.of(context));
    }

    @Override
    protected void executeCompletedEntry(StateContext<PaymentState, PaymentEvent> context) {
        var proxy = SagaContextProxy.of(context);
        if (Boolean.TRUE.equals(proxy.getIsRestoring())) {
            return;
        }
        paymentProcessingSaga.completedEntry(proxy);
    }

    @Override
    protected void executeFailedEntry(StateContext<PaymentState, PaymentEvent> context) {
        paymentProcessingSaga.failedEntry(SagaContextProxy.of(context));
    }
}

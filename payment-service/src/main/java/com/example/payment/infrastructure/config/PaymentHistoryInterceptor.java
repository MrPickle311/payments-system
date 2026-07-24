package com.example.payment.infrastructure.config;

import static org.springframework.statemachine.StateContext.Stage.STATE_CHANGED;

import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentHistoryInterceptor extends StateMachineListenerAdapter<PaymentState, PaymentEvent> {

    private static final String ROOT_REGION = "ROOT";
    public static final String AUTO = "AUTO";

    private final PaymentHistoryRepository paymentHistoryRepository;

    @Override
    @Transactional
    public void stateContext(StateContext<PaymentState, PaymentEvent> stateContext) {
        if (stateContext.getStage() == STATE_CHANGED) {
            processStateChange(stateContext);
        }
    }

    private void processStateChange(StateContext<PaymentState, PaymentEvent> context) {
        if (context.getSource() == null || context.getTarget() == null) {
            return;
        }
        var proxy = SagaContextProxy.of(context);
        Long paymentId = proxy.getPaymentId();
        if (Boolean.TRUE.equals(proxy.getIsRestoring())) {
            log.debug("[Interceptor] Skipping restoration: payment={}", paymentId);
            return;
        }
        savePaymentHistory(context, paymentId);
    }

    private void savePaymentHistory(StateContext<PaymentState, PaymentEvent> context, Long paymentId) {
        PaymentState source = context.getSource().getId();
        PaymentState target = context.getTarget().getId();
        PaymentEvent event = context.getEvent();
        String region = getRegion(context);

        log.info(
                "[Interceptor] Record transition for payment {} [Region: {}]: {} --({})--> {}",
                paymentId,
                region,
                source,
                event,
                target);

        PaymentHistory history = PaymentHistory.builder()
                .paymentId(paymentId)
                .region(region)
                .fromState(source.name())
                .toState(target.name())
                .event(event != null ? event.name() : AUTO)
                .timestamp(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        paymentHistoryRepository.save(history);
    }

    private String getRegion(StateContext<PaymentState, PaymentEvent> context) {
        if (context.getTarget() == null) {
            return ROOT_REGION;
        }
        PaymentState state = context.getTarget().getId();
        return switch (state) {
            case AUTH_PENDING, AUTH_APPROVED, AUTH_REJECTED, AUTHORIZED -> "Authorization";
            case FRAUD_EVALUATING, FRAUD_PASSED, FRAUD_DETECTED -> "FraudCheck";
            case LIMITS_EVALUATING, LIMITS_OK, LIMITS_EXCEEDED -> "LimitsCheck";
            case SANCTIONS_EVALUATING, SANCTIONS_CLEARED, SANCTIONS_HIT -> "SanctionsCheck";
            case FEE_EVALUATING, FEE_CALCULATED, FEE_CHARGED, FEE_FAILED -> "FeeCheck";
            default -> ROOT_REGION;
        };
    }
}

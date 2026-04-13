package com.example.payments.payment.application;

import com.example.payments.payment.application.dto.CreatePaymentRequest;
import com.example.payments.payment.domain.*;
import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.config.PaymentStateMachineInterceptor;
import com.example.payments.payment.infrastructure.config.PaymentStateMachinePersister;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

/**
 * Orchestrates payment lifecycle operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
    private final PaymentStateMachineInterceptor stateMachineInterceptor;
    private final PaymentStateMachinePersister stateMachinePersister;

    @Transactional
    @Observed(name = "create-payment")
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = Payment.builder()
                .transactionId(request.getTransactionId())
                .money(request.getMoney())
                .state(PaymentState.NEW.name())
                .build();

        payment.registerCreationEvent();
        payment = paymentRepository.save(payment);

        paymentHistoryRepository.save(PaymentHistory.builder()
                .paymentId(payment.getId())
                .fromState("—")
                .toState(PaymentState.NEW.name())
                .event("CREATED")
                .build());

        log.info("[Service] Created payment id={} transactionId={}",
                payment.getId(), payment.getTransactionId());
        return payment;
    }

    @Transactional(noRollbackFor = InvalidTransitionException.class)
    @Observed(name = "process-payment-event")
    public Payment processEvent(@SpanTag("payment.id") Long paymentId, PaymentEvent event) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        log.info("[Service] Processing event={} for payment={} (currentState={})",
                event, paymentId, payment.getState());

        StateMachine<PaymentState, PaymentEvent> sm =
                stateMachineFactory.getStateMachine(UUID.randomUUID().toString());

        try {
            sm.getStateMachineAccessor()
                    .doWithAllRegions(access ->
                            access.addStateMachineInterceptor(stateMachineInterceptor));

            stateMachinePersister.restore(sm, payment);

            Collection<PaymentState> stateBeforeIds = sm.getState().getIds();
            PaymentState rootStateBefore = payment.currentState();

            List<StateMachineEventResult<PaymentState, PaymentEvent>> results =
                    sm.sendEvent(Mono.just(
                            MessageBuilder.withPayload(event).build()
                    )).collectList().block();

            Collection<PaymentState> stateAfterIds = sm.getState().getIds();
            PaymentState rootStateAfter  = sm.getState().getId();

            boolean denied = results == null || results.isEmpty()
                    || results.stream().allMatch(
                            r -> r.getResultType() == StateMachineEventResult.ResultType.DENIED);
            if (denied) {
                throw new InvalidTransitionException(
                        String.format("Event [%s] is not a legal transition from state [%s] "
                                + "for payment [%d].",
                                event, payment.getState(), paymentId));
            }

            boolean isSelfTransition = (event == PaymentEvent.REDIRECT);
            boolean guardBlocked     = !isSelfTransition && stateAfterIds.equals(stateBeforeIds);

            if (guardBlocked) {
                if (event == PaymentEvent.AUTHORIZE) {
                    Integer fraudScore = sm.getExtendedState().get("fraudScore", Integer.class);
                    String  fraudRisk  = sm.getExtendedState().get("fraudRisk",  String.class);
                    log.warn("[Service] AUTHORIZE blocked by fraud guard (score={} risk={}) "
                            + "for payment={}. Auto-failing.", fraudScore, fraudRisk, paymentId);

                    sm.sendEvent(Mono.just(
                            MessageBuilder.withPayload(PaymentEvent.FAIL).build()
                    )).collectList().block();
                    stateMachinePersister.persist(sm, payment);
                    paymentRepository.save(payment);

                    throw new InvalidTransitionException(
                            String.format("Payment [%d] rejected by fraud check "
                                    + "(score=%d, risk=%s). Payment moved to FAILED.",
                                    paymentId, fraudScore, fraudRisk));
                }
                throw new InvalidTransitionException(
                        String.format("Transition [%s] was blocked by a guard for payment [%d] "
                                + "in state [%s].",
                                event, paymentId, payment.getState()));
            }

            stateMachinePersister.persist(sm, payment);
            payment.publishStateChange(rootStateBefore, rootStateAfter);

        } finally {
            sm.stop();
        }

        Payment saved = paymentRepository.save(payment);
        log.info("[Service] Payment {} transitioned to state {}", paymentId, saved.getState());
        return saved;
    }

    @Transactional(readOnly = true)
    @Observed(name = "get-payment")
    public Payment getPayment(@SpanTag("payment.id") Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    @Observed(name = "get-payment-history")
    public List<PaymentHistory> getPaymentHistory(@SpanTag("payment.id") Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new PaymentNotFoundException(paymentId);
        }
        return paymentHistoryRepository.findByPaymentIdOrderByTimestampAsc(paymentId);
    }
}

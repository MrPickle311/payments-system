package com.example.payments.payment.application;
import lombok.extern.slf4j.Slf4j;

import com.example.payments.payment.application.dto.CreatePaymentRequest;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.InvalidTransitionException;
import com.example.payments.payment.domain.PaymentNotFoundException;
import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.config.PaymentStateMachineInterceptor;
import com.example.payments.payment.infrastructure.config.PaymentStateMachinePersister;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;

import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import static com.example.payments.payment.domain.PaymentConstants.*;
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
                .fromState(INITIAL_FROM_STATE)
                .toState(PaymentState.NEW.name())
                .event(EVENT_CREATED)
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

        StateMachine<PaymentState, PaymentEvent> stateMachine =
                stateMachineFactory.getStateMachine(UUID.randomUUID().toString());

        try {
            configureStateMachine(stateMachine, payment);

            Collection<PaymentState> stateBeforeIds = stateMachine.getState().getIds();
            PaymentState rootStateBefore = payment.currentState();

            List<StateMachineEventResult<PaymentState, PaymentEvent>> results = sendEventToStateMachine(stateMachine, event);

            verifyTransitionAllowed(results, event, payment);

            Collection<PaymentState> stateAfterIds = stateMachine.getState().getIds();
            PaymentState rootStateAfter = stateMachine.getState().getId();

            GuardCheckContext context = GuardCheckContext.builder()
                    .stateMachine(stateMachine)
                    .payment(payment)
                    .event(event)
                    .stateBeforeIds(stateBeforeIds)
                    .stateAfterIds(stateAfterIds)
                    .build();

            handleBlockedGuards(context);

            stateMachinePersister.persist(stateMachine, payment);
            payment.publishStateChange(rootStateBefore, rootStateAfter);

        } finally {
            stateMachine.stop();
        }

        Payment saved = paymentRepository.save(payment);
        log.info("[Service] Payment {} transitioned to state {}", paymentId, saved.getState());
        return saved;
    }

    private void configureStateMachine(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access ->
                        access.addStateMachineInterceptor(stateMachineInterceptor));
        stateMachinePersister.restore(stateMachine, payment);
    }

    private List<StateMachineEventResult<PaymentState, PaymentEvent>> sendEventToStateMachine(StateMachine<PaymentState, PaymentEvent> stateMachine, PaymentEvent event) {
        return stateMachine.sendEvent(Mono.just(
                MessageBuilder.withPayload(event).build()
        )).collectList().block();
    }

    private void verifyTransitionAllowed(List<StateMachineEventResult<PaymentState, PaymentEvent>> results, PaymentEvent event, Payment payment) {
        boolean denied = results == null || results.isEmpty()
                || results.stream()
                        .allMatch(result -> result.getResultType() == StateMachineEventResult.ResultType.DENIED);
        if (denied) {
            throw new InvalidTransitionException(
                    String.format("Event [%s] is not a legal transition from state [%s] for payment [%d].",
                            event, payment.getState(), payment.getId()));
        }
    }

    private void handleBlockedGuards(GuardCheckContext context) {
        boolean isSelfTransition = (context.getEvent() == PaymentEvent.REDIRECT);
        boolean guardBlocked = !isSelfTransition && context.getStateAfterIds().equals(context.getStateBeforeIds());

        if (guardBlocked) {
            if (context.getEvent() == PaymentEvent.AUTHORIZE) {
                handleFraudGuardBlock(context.getStateMachine(), context.getPayment());
            }
            throw new InvalidTransitionException(
                    String.format("Transition [%s] was blocked by a guard for payment [%d] in state [%s].",
                            context.getEvent(), context.getPayment().getId(), context.getPayment().getState()));
        }
    }

    private void handleFraudGuardBlock(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
        Integer fraudScore = stateMachine.getExtendedState().get(FRAUD_SCORE, Integer.class);
        String fraudRisk = stateMachine.getExtendedState().get(FRAUD_RISK, String.class);
        log.warn("[Service] AUTHORIZE blocked by fraud guard (score={} risk={}) for payment={}. Auto-failing.", 
                fraudScore, fraudRisk, payment.getId());

        sendEventToStateMachine(stateMachine, PaymentEvent.FAIL);
        stateMachinePersister.persist(stateMachine, payment);
        paymentRepository.save(payment);

        throw new InvalidTransitionException(
                String.format("Payment [%d] rejected by fraud check (score=%d, risk=%s). Payment moved to FAILED.",
                        payment.getId(), fraudScore, fraudRisk));
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

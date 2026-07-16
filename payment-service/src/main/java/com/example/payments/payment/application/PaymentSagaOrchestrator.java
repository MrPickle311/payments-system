package com.example.payments.payment.application;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.payment.domain.*;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.config.PaymentStateMachineInterceptor;
import com.example.payments.payment.infrastructure.config.PaymentStateMachinePersister;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.NonNull;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.example.payments.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payments.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payments.payment.domain.enums.PaymentEvent.*;
import static com.example.payments.payment.domain.enums.PaymentEvent.FAIL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.statemachine.StateMachineEventResult.ResultType.DENIED;

@Component
@RequiredArgsConstructor
@Slf4j
class PaymentSagaOrchestrator {

    private final PaymentRepository paymentRepository;
    private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
    private final PaymentStateMachineInterceptor stateMachineInterceptor;
    private final PaymentStateMachinePersister stateMachinePersister;
    private final ObjectMapper objectMapper;

    @Transactional(noRollbackFor = InvalidTransitionException.class)
    @Observed(name = "process-payment-event")
    public Payment processEvent(@SpanTag("payment.id") Long paymentId, PaymentEvent event) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        StateMachine<PaymentState, PaymentEvent> sm =
                stateMachineFactory.getStateMachine(UUID.randomUUID().toString());
        try {
            configureStateMachine(sm, payment);
            processStateMachineEvent(sm, payment, event);
        } finally {
            sm.stop();
        }
        return savePayment(paymentId, payment);
    }

    @KafkaListener(topics = "payment-events", groupId = "payment-saga-group")
    public void consumeSagaEvent(ConsumerRecord<String, String> record) {
        Header typeHeader = record.headers().lastHeader("type");
        if (typeHeader == null) {
            log.warn("[Orchestrator] Missing type header in Kafka record");
            return;
        }
        String type = new String(typeHeader.value(), UTF_8);
        try {
            dispatchSagaEvent(type, record.value());
        } catch (Exception e) {
            log.error("[Orchestrator] Error processing event type={}: {}", type, e.getMessage());
        }
    }

    private Payment savePayment(Long paymentId, Payment payment) {
        Payment saved = paymentRepository.save(payment);
        log.info("[Orchestrator] Payment {} transitioned to state {}", paymentId, saved.getState());
        return saved;
    }

    private void processStateMachineEvent(StateMachine<PaymentState, PaymentEvent> sm,
                                          Payment payment, PaymentEvent event) {
        Collection<PaymentState> before = sm.getState().getIds();
        PaymentState rootBefore = payment.currentState();
        List<StateMachineEventResult<PaymentState, PaymentEvent>> results = sendEventToStateMachine(sm, event);
        verifyTransitionAllowed(results, event, payment);
        Collection<PaymentState> after = sm.getState().getIds();
        PaymentState rootAfter = sm.getState().getId();
        handleBlockedGuards(GuardCheckContext.builder().stateMachine(sm).payment(payment).event(event)
                .stateBeforeIds(before).stateAfterIds(after).build());
        stateMachinePersister.persist(sm, payment);
        payment.publishStateChange(rootBefore, rootAfter);
    }

    private void configureStateMachine(StateMachine<PaymentState, PaymentEvent> stateMachine,
                                       Payment payment) {
        stateMachine.addStateListener(stateMachineInterceptor); //TODO: do I need always add state listener ?
        stateMachinePersister.restore(stateMachine, payment);
    }

    private List<StateMachineEventResult<PaymentState, PaymentEvent>> sendEventToStateMachine(
            StateMachine<PaymentState, PaymentEvent> stateMachine, PaymentEvent event) {
        return stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(event).build()))
                .collectList().block(); //TODO: also move to mapper
    }

    private void verifyTransitionAllowed(
            List<StateMachineEventResult<PaymentState, PaymentEvent>> results,
            PaymentEvent event,
            Payment payment) {
        boolean denied = results == null || results.isEmpty() || results.stream()
                .allMatch(result -> result.getResultType() == DENIED);
        if (denied) {
            throw new InvalidTransitionException(
                    String.format("Event [%s] is not a legal transition from state [%s] for payment [%d].",
                            event, payment.getState(), payment.getId()));
        }
    }

    private void handleBlockedGuards(GuardCheckContext context) {
        boolean isSelfTransition = (context.getEvent() == REDIRECT);
        boolean guardBlocked =
                !isSelfTransition && context.getStateAfterIds().equals(context.getStateBeforeIds());
        if (guardBlocked) {
            if (context.getEvent() == AUTHORIZE) {
                handleFraudGuardBlock(context.getStateMachine(), context.getPayment());
            }
            throw new InvalidTransitionException(
                    String.format("Transition [%s] was blocked by a guard for payment [%d] in state [%s].",
                            context.getEvent(), context.getPayment().getId(), context.getPayment().getState()));
        }
    }

    private void handleFraudGuardBlock(StateMachine<PaymentState, PaymentEvent> stateMachine,
                                       Payment payment) {
        Integer fraudScore = stateMachine.getExtendedState().get(FRAUD_SCORE, Integer.class);
        String fraudRisk = stateMachine.getExtendedState().get(FRAUD_RISK, String.class);
        sendEventToStateMachine(stateMachine, FAIL);
        stateMachinePersister.persist(stateMachine, payment);
        paymentRepository.save(payment);
        throw new InvalidTransitionException(String.format(
                "Payment [%d] rejected by fraud check (score=%d, risk=%s). Payment moved to FAILED.",
                payment.getId(), fraudScore, fraudRisk));
    }

    private void dispatchSagaEvent(String type, String value) throws JsonProcessingException {
        if ("FundsReservedEvent".equals(type)) {
            DebitRequest.FundsReservedEvent event = objectMapper.readValue(value, DebitRequest.FundsReservedEvent.class);
            handleFundsReserved(event);
        } else if ("FundsReservationFailedEvent".equals(type)) {
            DebitRequest.FundsReservationFailedEvent event =
                    objectMapper.readValue(value, DebitRequest.FundsReservationFailedEvent.class);
            handleFundsReservationFailed(event);
        } else if ("JournalEntryPostedEvent".equals(type)) {
            DebitRequest.JournalEntryPostedEvent event = objectMapper.readValue(value, DebitRequest.JournalEntryPostedEvent.class);
            handleJournalEntryPosted(event);
        }
    }

    private void handleFundsReserved(DebitRequest.FundsReservedEvent event) {
        log.info("[Orchestrator] Handling FundsReservedEvent for paymentId={} type={} amount={}",
                event.getPaymentId(), event.getType(), event.getAmount());
        Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.getPaymentId()));
        if (PaymentState.COMPENSATING.name().equalsIgnoreCase(payment.getState())) {
            processEvent(event.getPaymentId(), COMPENSATION_COMPLETE);
        } else if ("BASE".equalsIgnoreCase(event.getType())) { //TODO: move string to const or enum
            processEvent(event.getPaymentId(), FUNDS_RESERVED);
        } else if ("FEE".equalsIgnoreCase(event.getType())) { //TODO: move string to const or enum
            processEvent(event.getPaymentId(), FEE_RESERVED);
        }
    }

    private void handleFundsReservationFailed(DebitRequest.FundsReservationFailedEvent event) {
        log.warn("[Orchestrator] Handling FundsReservationFailedEvent for paymentId={} reason={}",
                event.getPaymentId(), event.getReason());
        Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.getPaymentId()));
        if (PaymentState.FUNDS_RESERVING.name().equalsIgnoreCase(payment.getState())) {
            processEvent(event.getPaymentId(), FUNDS_RESERVATION_FAILED);
        } else if (PaymentState.FEE_RESERVING.name().equalsIgnoreCase(payment.getState())) {
            processEvent(event.getPaymentId(), FEE_RESERVATION_FAILED);
        }
    }

    private void handleJournalEntryPosted(DebitRequest.JournalEntryPostedEvent event) {
        log.info("[Orchestrator] Handling JournalEntryPostedEvent for paymentId={} entryId={}",
                event.getPaymentId(), event.getEntryId());
        processEvent(event.getPaymentId(), ENTRIES_POSTED);
    }
}

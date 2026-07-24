package com.example.payment.infrastructure.statemachine;

import static com.example.payment.domain.PaymentConstants.SOURCE_CURRENCY;
import static com.example.payment.domain.PaymentConstants.SOURCE_USER_ID;
import static com.example.payment.domain.PaymentConstants.TARGET_CURRENCY;
import static com.example.payment.domain.PaymentConstants.TARGET_USER_ID;
import static com.example.payment.domain.enums.PaymentEvent.AUTHORIZE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.REDIRECT;
import static com.example.payment.domain.enums.PaymentState.CANCELED;
import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FAILED;
import static com.example.payment.domain.enums.PaymentState.REFUNDED;
import static org.springframework.statemachine.StateMachineEventResult.ResultType.DENIED;

import com.example.payment.application.saga.GuardCheckContext;
import com.example.payment.application.saga.ParallelSagaJoinInterceptor;
import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.InvalidTransitionException;
import com.example.payment.domain.Payment;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.infrastructure.config.PaymentHistoryInterceptor;
import com.example.payment.infrastructure.config.PaymentStateMachinePersister;
import com.example.payment.infrastructure.config.PaymentStateMachinePersistingInterceptor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStateMachineManager {

    private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
    private final PaymentHistoryInterceptor stateMachineInterceptor;
    private final PaymentStateMachinePersister stateMachinePersister;
    private final ParallelSagaJoinInterceptor parallelSagaJoinInterceptor;
    private final PaymentStateMachinePersistingInterceptor persistingInterceptor;

    public PaymentState execute(Payment payment, PaymentEvent event) {
        StateMachine<PaymentState, PaymentEvent> sm =
                stateMachineFactory.getStateMachine(UUID.randomUUID().toString());
        try {
            configureStateMachine(sm, payment);
            return processStateMachineEvent(sm, payment, event);
        } finally {
            stopIfTerminal(sm, payment);
        }
    }

    private void configureStateMachine(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
        stateMachine.addStateListener(stateMachineInterceptor);
        stateMachine.getStateMachineAccessor().doWithAllRegions(accessor -> {
            accessor.addStateMachineInterceptor(parallelSagaJoinInterceptor);
            accessor.addStateMachineInterceptor(persistingInterceptor);
        });
        stateMachinePersister.restore(stateMachine, payment);
        var extState = stateMachine.getExtendedState().getVariables();
        extState.putIfAbsent(SOURCE_USER_ID, payment.getSourceUserId());
        extState.putIfAbsent(TARGET_USER_ID, payment.getTargetUserId());
        extState.putIfAbsent(SOURCE_CURRENCY, payment.getSourceCurrency());
        extState.putIfAbsent(TARGET_CURRENCY, payment.getTargetCurrency());
    }

    private PaymentState processStateMachineEvent(
            StateMachine<PaymentState, PaymentEvent> sm, Payment payment, PaymentEvent event) {
        Collection<PaymentState> before = sm.getState().getIds();
        PaymentState rootBefore = payment.currentState();
        verifyTransitionAllowed(sendEventToStateMachine(sm, event), event, payment);
        Collection<PaymentState> after = sm.getState().getIds();
        PaymentState rootAfter = sm.getState().getId();

        handleBlockedGuards(GuardCheckContext.builder()
                .stateMachine(sm)
                .payment(payment)
                .event(event)
                .stateBeforeIds(before)
                .stateAfterIds(after)
                .build());

        stateMachinePersister.persist(sm, payment);
        payment.publishStateChange(rootBefore, rootAfter);
        return rootAfter;
    }

    private void stopIfTerminal(StateMachine<PaymentState, PaymentEvent> sm, Payment payment) {
        if ((payment != null && payment.isTerminal()) || isStateMachineTerminal(sm)) {
            sm.stop();
        }
    }

    private boolean isStateMachineTerminal(StateMachine<PaymentState, PaymentEvent> sm) {
        var state = sm.getState();
        return state != null
                && (state.getId() == COMPLETED
                        || state.getId() == FAILED
                        || state.getId() == CANCELED
                        || state.getId() == REFUNDED);
    }

    private List<StateMachineEventResult<PaymentState, PaymentEvent>> sendEventToStateMachine(
            StateMachine<PaymentState, PaymentEvent> sm, PaymentEvent event) {
        log.info(
                "Sending event {} to state machine currently in state {}",
                event,
                sm.getState().getId());
        return sm.sendEvent(Mono.just(MessageBuilder.withPayload(event).build()))
                .collectList()
                .block();
    }

    private void verifyTransitionAllowed(
            List<StateMachineEventResult<PaymentState, PaymentEvent>> results, PaymentEvent event, Payment payment) {
        boolean denied = results == null
                || results.isEmpty()
                || results.stream().allMatch(result -> result.getResultType() == DENIED);
        if (denied) {
            throw new InvalidTransitionException(String.format(
                    "Event [%s] is not a legal transition from state [%s] for payment [%d].",
                    event, payment.getState(), payment.getId()));
        }
    }

    private void handleBlockedGuards(GuardCheckContext context) {
        boolean isSelfTransition = context.getEvent() == REDIRECT;
        boolean guardBlocked = !isSelfTransition && context.getStateAfterIds().equals(context.getStateBeforeIds());

        if (guardBlocked) {
            if (context.getEvent() == AUTHORIZE) {
                handleFraudGuardBlock(context.getStateMachine(), context.getPayment());
            }
            throw new InvalidTransitionException(String.format(
                    "Transition [%s] was blocked by a guard for payment [%d] in state [%s].",
                    context.getEvent(),
                    context.getPayment().getId(),
                    context.getPayment().getState()));
        }
    }

    private void handleFraudGuardBlock(StateMachine<PaymentState, PaymentEvent> stateMachine, Payment payment) {
        var proxy = SagaContextProxy.of(stateMachine);
        Integer score = proxy.getFraudScore();
        String risk = proxy.getFraudRisk();
        payment.markFraudEvaluation(score, risk);
        log.warn(
                "[Service] AUTHORIZE blocked by fraud guard (score={} risk={}) for payment={}.",
                score,
                risk,
                payment.getId());
        sendEventToStateMachine(stateMachine, FAIL);
        stateMachinePersister.persist(stateMachine, payment);
        throw new InvalidTransitionException(String.format(
                "Payment [%d] rejected by fraud check (score=%d, risk=%s).", payment.getId(), score, risk));
    }
}

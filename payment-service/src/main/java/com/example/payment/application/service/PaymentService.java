package com.example.payment.application.service;

import com.example.payment.application.saga.GuardCheckContext;
import com.example.payment.application.dto.CreatePaymentRequest;
import com.example.payment.application.mapper.PaymentApplicationMapper;
import com.example.payment.application.saga.ParallelSagaJoinInterceptor;
import com.example.payment.domain.InvalidTransitionException;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentConstants;
import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import com.example.payment.domain.PaymentNotFoundException;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.infrastructure.config.PaymentStateMachineInterceptor;
import com.example.payment.infrastructure.config.PaymentStateMachinePersister;
import com.example.payment.infrastructure.config.PaymentStateMachinePersistingInterceptor;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import static com.example.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payment.domain.PaymentConstants.SOURCE_USER_ID;
import static com.example.payment.domain.PaymentConstants.TARGET_USER_ID;
import static com.example.payment.domain.PaymentConstants.SOURCE_CURRENCY;
import static com.example.payment.domain.PaymentConstants.TARGET_CURRENCY;
import static com.example.payment.domain.enums.PaymentEvent.AUTHORIZE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.REDIRECT;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentHistoryRepository paymentHistoryRepository;
  private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
  private final PaymentStateMachineInterceptor stateMachineInterceptor;
  private final PaymentStateMachinePersister stateMachinePersister;
  private final ParallelSagaJoinInterceptor parallelSagaJoinInterceptor;
  private final PaymentStateMachinePersistingInterceptor persistingInterceptor;
  private final PaymentApplicationMapper paymentApplicationMapper;

  @Transactional
  @Observed(name = "create-payment")
  public Payment createPayment(CreatePaymentRequest request) {
    Payment payment = paymentApplicationMapper.toNewDomainPayment(request);

    payment.registerCreationEvent();
    payment = paymentRepository.save(payment);

    paymentHistoryRepository.save(PaymentHistory.builder().paymentId(payment.getId())
        .fromState(PaymentConstants.INITIAL_FROM_STATE).toState(PaymentState.NEW.name())
        .event(PaymentConstants.EVENT_CREATED).build());

    log.info("[Service] Created payment id={} transactionId={}", payment.getId(),
        payment.getTransactionId());
    return payment;
  }

  @Transactional(noRollbackFor = InvalidTransitionException.class)
  @Observed(name = "initiate-payment")
  public Payment initiatePayment(Long paymentId) {
    return processEvent(paymentId, PaymentEvent.INITIATE);
  }

  @Observed(name = "process-payment-event")
  public Payment processEvent(@SpanTag("payment.id") Long paymentId, PaymentEvent event) {
    Payment payment = paymentRepository.findByIdWithLock(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    log.info("[Service] Processing event={} for payment={} (currentState={})", event, paymentId,
        payment.getState());
    StateMachine<PaymentState, PaymentEvent> sm =
        stateMachineFactory.getStateMachine(UUID.randomUUID().toString());
    try {
      configureStateMachine(sm, payment);
      processStateMachineEvent(sm, payment, event);
    } finally {
      stopIfTerminal(sm);
    }
    return savePayment(paymentId, payment);
  }

  private void stopIfTerminal(StateMachine<PaymentState, PaymentEvent> sm) {
    var state = sm.getState();
    if (state != null
        && (state.getId() == PaymentState.COMPLETED || state.getId() == PaymentState.FAILED
            || state.getId() == PaymentState.CANCELED || state.getId() == PaymentState.REFUNDED)) {
      sm.stopReactively().block();
    }
  }

  private @NonNull Payment savePayment(Long paymentId, Payment payment) {
    Payment saved = paymentRepository.save(payment);
    log.info("[Service] Payment {} transitioned to state {}", paymentId, saved.getState());
    return saved;
  }

  private void processStateMachineEvent(StateMachine<PaymentState, PaymentEvent> sm,
      Payment payment, PaymentEvent event) {
    Collection<PaymentState> before = sm.getState().getIds();
    PaymentState rootBefore = payment.currentState();
    verifyTransitionAllowed(sendEventToStateMachine(sm, event), event, payment);
    Collection<PaymentState> after = sm.getState().getIds();
    PaymentState rootAfter = sm.getState().getId();

    handleBlockedGuards(GuardCheckContext.builder().stateMachine(sm).payment(payment).event(event)
        .stateBeforeIds(before).stateAfterIds(after).build());

    stateMachinePersister.persist(sm, payment);
    payment.publishStateChange(rootBefore, rootAfter);
  }

  private void configureStateMachine(StateMachine<PaymentState, PaymentEvent> stateMachine,
      Payment payment) {
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

  private List<StateMachineEventResult<PaymentState, PaymentEvent>> sendEventToStateMachine(
      StateMachine<PaymentState, PaymentEvent> sm, PaymentEvent event) {
    log.info("Sending event {} to state machine currently in state {}", event,
        sm.getState().getId());
    return sm.sendEvent(Mono.just(MessageBuilder.withPayload(event).build())).collectList().block();
  }

  private void verifyTransitionAllowed(
      List<StateMachineEventResult<PaymentState, PaymentEvent>> results, PaymentEvent event,
      Payment payment) {
    boolean denied = results == null || results.isEmpty() || results.stream()
        .allMatch(result -> result.getResultType() == StateMachineEventResult.ResultType.DENIED);
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
    log.warn(
        "[Service] AUTHORIZE blocked by fraud guard (score={} risk={}) for payment={}. Auto-failing.",
        fraudScore, fraudRisk, payment.getId());

    sendEventToStateMachine(stateMachine, FAIL);
    stateMachinePersister.persist(stateMachine, payment);
    paymentRepository.save(payment);

    throw new InvalidTransitionException(String.format(
        "Payment [%d] rejected by fraud check (score=%d, risk=%s). Payment moved to FAILED.",
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

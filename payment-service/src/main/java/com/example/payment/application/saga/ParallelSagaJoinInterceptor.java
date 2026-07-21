package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EXCEEDED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_HIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelSagaJoinInterceptor
    extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

  private static final Set<PaymentState> SUCCESS_STATES =
      EnumSet.of(AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);

  private static final Set<PaymentState> FAILURE_STATES =
      EnumSet.of(AUTH_REJECTED, FRAUD_DETECTED, LIMITS_EXCEEDED, SANCTIONS_HIT, FEE_FAILED);

  private static final int EXPECTED_REGION_COUNT = 5;

  private final ExecutorService virtualThreadExecutor;

  @Override
  public void postStateChange(State<PaymentState, PaymentEvent> state,
      Message<PaymentEvent> message, Transition<PaymentState, PaymentEvent> transition,
      StateMachine<PaymentState, PaymentEvent> stateMachine,
      StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

    if (stateMachine != rootStateMachine) {
      checkParallelJoinForAllRegions(rootStateMachine);
    }
  }

  private void checkParallelJoinForAllRegions(
      StateMachine<PaymentState, PaymentEvent> rootStateMachine) {
    State<PaymentState, PaymentEvent> currentState = rootStateMachine.getState();
    if (currentState == null || !isProcessingState(currentState)) {
      return;
    }
    Collection<PaymentState> ids = currentState.getIds();
    if (ids != null) {
      long finishedRegions = ids.stream()
          .filter(s -> SUCCESS_STATES.contains(s) || FAILURE_STATES.contains(s)).count();
      if (finishedRegions >= EXPECTED_REGION_COUNT) {
        triggerCompletion(rootStateMachine, hasAnyRegionFailed(ids));
      }
    }
  }

  private boolean hasAnyRegionFailed(Collection<PaymentState> ids) {
    return ids.stream().anyMatch(FAILURE_STATES::contains);
  }

  private static boolean isProcessingState(State<PaymentState, PaymentEvent> currentState) {
    return currentState.getIds().contains(PROCESSING);
  }

  private void triggerCompletion(StateMachine<PaymentState, PaymentEvent> rootStateMachine,
      boolean anyFailed) {
    Long paymentId = rootStateMachine.getExtendedState().get(PAYMENT_ID, Long.class);
    PaymentEvent event = anyFailed ? PaymentEvent.FAIL : PaymentEvent.COMPLETE;
    log.info("[JoinInterceptor] Triggering {} for paymentId={}", event, paymentId);
    CompletableFuture
        .runAsync(() -> SagaContextProxy.sendEventWithRetries(rootStateMachine, event, paymentId), virtualThreadExecutor);
  }
}

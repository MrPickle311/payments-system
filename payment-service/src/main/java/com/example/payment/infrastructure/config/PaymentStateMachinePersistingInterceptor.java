package com.example.payment.infrastructure.config;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentStateMachinePersistingInterceptor
    extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

  private static final String PAYMENT_ID = "paymentId";
  private static final Set<PaymentState> TERMINAL_STATES = EnumSet.of(PaymentState.COMPLETED,
      PaymentState.FAILED, PaymentState.CANCELED, PaymentState.REFUNDED);

  private final PaymentRepository paymentRepository;
  private final PaymentStateMachinePersister persister;

  @Override
  @Transactional
  public void postStateChange(State<PaymentState, PaymentEvent> state,
      Message<PaymentEvent> message, Transition<PaymentState, PaymentEvent> transition,
      StateMachine<PaymentState, PaymentEvent> stateMachine,
      StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

    Long paymentId = rootStateMachine.getExtendedState().get(PAYMENT_ID, Long.class);
    if (paymentId != null) {
      saveAndStopIfTerminal(rootStateMachine, state.getId(), paymentId);
    }
  }

  private void saveAndStopIfTerminal(StateMachine<PaymentState, PaymentEvent> rootStateMachine,
      PaymentState stateId, Long paymentId) {
    paymentRepository.findByIdWithLock(paymentId).ifPresent(payment -> {
      persister.persist(rootStateMachine, payment);
      Payment saved = paymentRepository.save(payment);
      log.info("[PersistingInterceptor] Saved state {} for payment {}", saved.getState(),
          paymentId);
    });

    if (TERMINAL_STATES.contains(stateId)) {
      scheduleStop(rootStateMachine, stateId, paymentId);
    }
  }

  private void scheduleStop(StateMachine<PaymentState, PaymentEvent> rootStateMachine,
      PaymentState stateId, Long paymentId) {
    log.info("[PersistingInterceptor] Terminal state {} reached. Scheduling stop for payment {}",
        stateId, paymentId);
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      rootStateMachine.stopReactively().subscribe();
    });
  }
}

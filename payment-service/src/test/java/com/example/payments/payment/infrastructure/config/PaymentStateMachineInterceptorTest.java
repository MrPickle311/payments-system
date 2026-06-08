package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.springframework.messaging.Message;
import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PaymentStateMachineInterceptorTest {
  private static final String PAYMENT_ID_KEY = "paymentId";
  private static final String IS_RESTORING_KEY = "isRestoring";


  @Mock
  private PaymentHistoryRepository paymentHistoryRepository;
  @Mock
  private State<PaymentState, PaymentEvent> state;
  @Mock
  private Message<PaymentEvent> message;
  @Mock
  private Transition<PaymentState, PaymentEvent> transition;
  @Mock
  private StateMachine<PaymentState, PaymentEvent> stateMachine;
  @Mock
  private StateMachine<PaymentState, PaymentEvent> rootStateMachine;
  @Mock
  private ExtendedState extendedState;

  private PaymentStateMachineInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new PaymentStateMachineInterceptor(paymentHistoryRepository);
  }

  @Test
  void testPreStateChangeTransitionNull() {
    interceptor.preStateChange(state, message, null, stateMachine, rootStateMachine);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testPreStateChangeSourceNull() {
    when(transition.getSource()).thenReturn(null);
    interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testPreStateChangeIsRestoringTrue() {
    State<PaymentState, PaymentEvent> sourceState = mock(State.class);
    State<PaymentState, PaymentEvent> targetState = mock(State.class);

    when(transition.getSource()).thenReturn(sourceState);
    when(transition.getTarget()).thenReturn(targetState);
    when(sourceState.getId()).thenReturn(PaymentState.NEW);
    when(targetState.getId()).thenReturn(PaymentState.AUTHORIZED);
    when(stateMachine.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get(PAYMENT_ID_KEY, Long.class)).thenReturn(1L);
    when(extendedState.get(IS_RESTORING_KEY, Boolean.class)).thenReturn(true);

    interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testPreStateChangeSaveHistory() {
    State<PaymentState, PaymentEvent> source = mock(State.class);
    State<PaymentState, PaymentEvent> target = mock(State.class);

    when(source.getId()).thenReturn(PaymentState.NEW);
    when(target.getId()).thenReturn(PaymentState.PROCESSING);

    when(transition.getSource()).thenReturn(source);
    when(transition.getTarget()).thenReturn(target);

    when(stateMachine.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get("paymentId", Long.class)).thenReturn(1L);
    when(extendedState.get("isRestoring", Boolean.class)).thenReturn(false);

    interceptor.preStateChange(source, message, transition, stateMachine, stateMachine);
    verify(paymentHistoryRepository).save(any(PaymentHistory.class));
  }
}

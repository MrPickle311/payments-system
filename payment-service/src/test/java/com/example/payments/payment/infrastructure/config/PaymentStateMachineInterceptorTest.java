package com.example.payments.payment.infrastructure.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.state.State;

@ExtendWith(MockitoExtension.class)
class PaymentStateMachineInterceptorTest {
  private static final String PAYMENT_ID_KEY = "paymentId";
  private static final String IS_RESTORING_KEY = "isRestoring";

  @Mock
  private PaymentHistoryRepository paymentHistoryRepository;
  @Mock
  private StateContext<PaymentState, PaymentEvent> stateContext;
  @Mock
  private ExtendedState extendedState;

  private PaymentStateMachineInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new PaymentStateMachineInterceptor(paymentHistoryRepository);
    when(stateContext.getStage()).thenReturn(StateContext.Stage.STATE_CHANGED);
  }

  @Test
  void testStateContextSourceNull() {
    when(stateContext.getSource()).thenReturn(null);
    interceptor.stateContext(stateContext);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testStateContextTargetNull() {
    State<PaymentState, PaymentEvent> sourceState = mock(State.class);
    when(stateContext.getSource()).thenReturn(sourceState);
    when(stateContext.getTarget()).thenReturn(null);
    interceptor.stateContext(stateContext);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testStateContextIsRestoringTrue() {
    State<PaymentState, PaymentEvent> sourceState = mock(State.class);
    State<PaymentState, PaymentEvent> targetState = mock(State.class);

    when(stateContext.getSource()).thenReturn(sourceState);
    when(stateContext.getTarget()).thenReturn(targetState);
    when(stateContext.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get(PAYMENT_ID_KEY, Long.class)).thenReturn(1L);
    when(extendedState.get(IS_RESTORING_KEY, Boolean.class)).thenReturn(true);

    interceptor.stateContext(stateContext);
    verifyNoInteractions(paymentHistoryRepository);
  }

  @Test
  void testStateContextSaveHistory() {
    State<PaymentState, PaymentEvent> source = mock(State.class);
    State<PaymentState, PaymentEvent> target = mock(State.class);
    when(source.getId()).thenReturn(PaymentState.NEW);
    when(target.getId()).thenReturn(PaymentState.PROCESSING);
    when(stateContext.getSource()).thenReturn(source);
    when(stateContext.getTarget()).thenReturn(target);
    when(stateContext.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get(PAYMENT_ID_KEY, Long.class)).thenReturn(1L);
    when(extendedState.get(IS_RESTORING_KEY, Boolean.class)).thenReturn(false);
    interceptor.stateContext(stateContext);
    verify(paymentHistoryRepository).save(any(PaymentHistory.class));
  }
}

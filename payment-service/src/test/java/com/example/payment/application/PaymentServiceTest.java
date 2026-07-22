package com.example.payment.application;

import com.example.payment.application.dto.CreatePaymentRequest;
import com.example.payment.application.mapper.PaymentApplicationMapper;
import com.example.payment.application.saga.ParallelSagaJoinInterceptor;
import com.example.payment.application.service.PaymentService;
import com.example.payment.domain.InvalidTransitionException;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import com.example.payment.domain.PaymentNotFoundException;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.infrastructure.config.PaymentHistoryInterceptor;
import com.example.payment.infrastructure.config.PaymentStateMachinePersister;
import com.example.payment.infrastructure.config.PaymentStateMachinePersistingInterceptor;
import com.example.payment.infrastructure.statemachine.PaymentStateMachineManager;
import com.example.payments.common.sharedkernel.Money;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.access.StateMachineAccessor;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.example.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payment.domain.enums.PaymentEvent.AUTHORIZE;
import static com.example.payment.domain.enums.PaymentState.AUTHORIZED;
import static com.example.payment.domain.enums.PaymentState.NEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.statemachine.StateMachineEventResult.ResultType.ACCEPTED;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private PaymentHistoryRepository paymentHistoryRepository;
  @Mock
  private StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
  @Mock
  private PaymentHistoryInterceptor stateMachineInterceptor;
  @Mock
  private PaymentStateMachinePersister stateMachinePersister;

  @Mock
  private ParallelSagaJoinInterceptor parallelSagaJoinInterceptor;
  @Mock
  private PaymentStateMachinePersistingInterceptor persistingInterceptor;
  @Mock
  private PaymentApplicationMapper paymentApplicationMapper;

  private PaymentStateMachineManager stateMachineManager;
  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    stateMachineManager =
        new PaymentStateMachineManager(stateMachineFactory, stateMachineInterceptor,
            stateMachinePersister, parallelSagaJoinInterceptor, persistingInterceptor);
    paymentService = new PaymentService(paymentRepository, paymentHistoryRepository,
        paymentApplicationMapper, stateMachineManager);
  }

  private static final String TX1 = "tx1";
  private static final String HUNDRED = "100.00";
  private static final String USD = "USD";

  @Test
  void testCreatePayment() {
    CreatePaymentRequest request =
        new CreatePaymentRequest(TX1, new BigDecimal(HUNDRED), USD, 101L, 202L, USD, "EUR");
    Payment savedPayment = createPayment();
    when(paymentApplicationMapper.toNewDomainPayment(any(CreatePaymentRequest.class)))
        .thenReturn(savedPayment);
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    Payment result = paymentService.createPayment(request);

    assertNotNull(result);
    assertEquals(1L, result.getId());
    verify(paymentRepository).save(any(Payment.class));
    verify(paymentHistoryRepository).save(any(PaymentHistory.class));
  }

  private Payment createPayment() {
    return Payment.builder().id(1L).transactionId(TX1).money(Money.of(new BigDecimal(HUNDRED), USD))
        .state(NEW.name()).build();
  }

  @Test
  void testGetPayment() {
    Payment payment = Payment.builder().id(1L).build();
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    Payment result = paymentService.getPayment(1L);

    assertNotNull(result);
    assertEquals(1L, result.getId());
  }

  @Test
  void testGetPaymentNotFound() {
    when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(PaymentNotFoundException.class, () -> paymentService.getPayment(1L));
  }

  @Test
  void testGetPaymentHistory() {
    PaymentHistory history = PaymentHistory.builder().id(1L).build();
    when(paymentRepository.existsById(1L)).thenReturn(true);
    when(paymentHistoryRepository.findByPaymentIdOrderByTimestampAsc(1L))
        .thenReturn(List.of(history));

    List<PaymentHistory> result = paymentService.getPaymentHistory(1L);

    assertEquals(1, result.size());
  }

  @Test
  void testGetPaymentHistoryNotFound() {
    when(paymentRepository.existsById(1L)).thenReturn(false);

    assertThrows(PaymentNotFoundException.class, () -> paymentService.getPaymentHistory(1L));
  }

  @Test
  void testProcessEventPaymentNotFound() {
    when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(PaymentNotFoundException.class, () -> paymentService.processEvent(1L, AUTHORIZE));
  }

  @Test
  void testProcessEventFraudGuardBlocked() {
    Payment payment = Payment.builder().id(1L).state(NEW.name()).build();
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    StateMachine<PaymentState, PaymentEvent> sm = setupBlockedMachine(NEW);
    ExtendedState extendedState = sm.getExtendedState();
    when(extendedState.get(FRAUD_SCORE, Integer.class)).thenReturn(90);
    when(extendedState.get(FRAUD_RISK, String.class)).thenReturn("HIGH");

    assertThrows(InvalidTransitionException.class,
        () -> paymentService.processEvent(1L, AUTHORIZE));
  }

  private StateMachine<PaymentState, PaymentEvent> setupBlockedMachine(PaymentState mockState) {
    StateMachine<PaymentState, PaymentEvent> sm = mock(StateMachine.class);
    State<PaymentState, PaymentEvent> state = mock(State.class);
    when(stateMachineFactory.getStateMachine(anyString())).thenReturn(sm);
    when(sm.getState()).thenReturn(state);
    when(state.getIds()).thenReturn(List.of(mockState));
    when(state.getId()).thenReturn(mockState);
    mockSendEventAndAccessor(sm);
    return sm;
  }

  private void mockSendEventAndAccessor(StateMachine<PaymentState, PaymentEvent> sm) {
    StateMachineEventResult<PaymentState, PaymentEvent> result =
        mock(StateMachineEventResult.class);
    lenient().when(result.getResultType()).thenReturn(ACCEPTED);
    lenient().when(sm.sendEvent(any(Mono.class))).thenReturn(Flux.just(result));
    StateMachineAccessor<PaymentState, PaymentEvent> accessor = mock(StateMachineAccessor.class);
    lenient().when(sm.getStateMachineAccessor()).thenReturn(accessor);
    ExtendedState extendedState = mock(ExtendedState.class);
    lenient().when(sm.getExtendedState()).thenReturn(extendedState);
    lenient().when(extendedState.getVariables()).thenReturn(new HashMap<>());
  }

  @Test
  void testProcessEventSuccess() {
    Payment payment = Payment.builder().id(1L).state(NEW.name()).build();
    when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

    setupSuccessMachine();
    when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

    Payment processed = paymentService.processEvent(1L, AUTHORIZE);
    assertNotNull(processed);
    verify(paymentRepository).save(any(Payment.class));
  }

  private void setupSuccessMachine() {
    StateMachine<PaymentState, PaymentEvent> sm = mock(StateMachine.class);
    State<PaymentState, PaymentEvent> stateBefore = mock(State.class);
    State<PaymentState, PaymentEvent> stateAfter = mock(State.class);
    when(stateMachineFactory.getStateMachine(anyString())).thenReturn(sm);
    when(sm.getState()).thenReturn(stateBefore, stateAfter);
    when(stateBefore.getIds()).thenReturn(List.of(NEW));
    when(stateAfter.getIds()).thenReturn(List.of(AUTHORIZED));
    when(stateAfter.getId()).thenReturn(AUTHORIZED);
    mockSendEventAndAccessor(sm);
  }
}

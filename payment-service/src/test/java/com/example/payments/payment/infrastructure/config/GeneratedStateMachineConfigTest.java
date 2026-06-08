package com.example.payments.payment.infrastructure.config;


import com.example.payments.payment.domain.InvalidTransitionException;
import com.example.payments.sharedkernel.Money;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.eq;
import java.util.List;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import com.example.payments.payment.application.PaymentService;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.statemachine.config.StateMachineBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;


class GeneratedStateMachineConfigTest {
  private static final String LOW_STR = "LOW";
  private static final String ALLOW_STR = "ALLOW";
  private static final String HUNDRED = "100.00";
  private static final String USD_STR = "USD";
  private static final String TEN_THOUSAND = "10000.00";
  private static final String HIGH_STR = "HIGH";
  private static final String BLOCK_STR = "BLOCK";


  private FraudCheckPort fraudCheckService;
  private FeeCalculationPort feeCalculationService;
  private WalletClient walletClient;
  private LedgerPublisher ledgerPublisher;
  private PaymentService paymentService;
  private Payment testPayment;
  private PaymentRepository paymentRepository;
  private PaymentHistoryRepository paymentHistoryRepository;
  private StateMachine<PaymentState, PaymentEvent> lastStateMachine;


  @BeforeEach
  void setUp() {
    initServices();
    initMockResponses();
    initRepositories();
    initPaymentService();
  }

  private void initServices() {
    fraudCheckService = mock(FraudCheckPort.class);
    feeCalculationService = mock(FeeCalculationPort.class);
    walletClient = mock(WalletClient.class);
    ledgerPublisher = mock(LedgerPublisher.class);
  }

  private void initMockResponses() {
    when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
        .thenReturn(new FraudCheckPort.FraudResult(10, LOW_STR, ALLOW_STR));

    when(feeCalculationService.calculate(any(Money.class)))
        .thenReturn(new FeeBreakdown(Money.of(HUNDRED, USD_STR), Money.of("2.9000", USD_STR),
            Money.of("0.30", USD_STR), Money.of("3.2000", USD_STR), Money.of("96.8000", USD_STR)));
  }

  private void initRepositories() {
    paymentRepository = mock(PaymentRepository.class);
    paymentHistoryRepository = mock(PaymentHistoryRepository.class);

    testPayment =
        Payment.builder().id(1L).transactionId("txn_123").money(Money.of(HUNDRED, USD_STR))
            .state(PaymentState.NEW.name()).createdAt(LocalDateTime.now()).build();

    when(paymentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testPayment));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
  }

  private void initPaymentService() {
    StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory =
        mock(StateMachineFactory.class);
    when(stateMachineFactory.getStateMachine(anyString())).thenAnswer(i -> {
      lastStateMachine = buildStateMachine();
      return lastStateMachine;
    });

    PaymentStateMachineInterceptor interceptor =
        new PaymentStateMachineInterceptor(paymentHistoryRepository);
    PaymentStateMachinePersister persister = new PaymentStateMachinePersister();

    paymentService = new PaymentService(paymentRepository, paymentHistoryRepository,
        stateMachineFactory, interceptor, persister);
  }


  private StateMachine<PaymentState, PaymentEvent> buildStateMachine() throws Exception {
    StateMachineConfig config = new StateMachineConfig(fraudCheckService, feeCalculationService,
        walletClient, ledgerPublisher);

    StateMachineBuilder.Builder<PaymentState, PaymentEvent> builder = StateMachineBuilder.builder();

    config.configure(builder.configureConfiguration());
    config.configure(builder.configureStates());
    config.configure(builder.configureTransitions());

    StateMachine<PaymentState, PaymentEvent> sm = builder.build();
    sm.start();
    return sm;
  }

  @Nested
  @DisplayName("State Transitions")
  class StateTransitions {

    @Test
    @DisplayName("Initial state is NEW")
    void initialStateIsNew() {
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.NEW);
    }

    @Test
    @DisplayName("NEW → PROCESSING on INITIATE")
    void newToProcessingOnInitiate() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.PROCESSING,
          PaymentState.AUTH_PENDING, PaymentState.FRAUD_EVALUATING);
    }

    @Test
    @DisplayName("PROCESSING → FAILED on FAIL")
    void processingToFailedOnFail() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.FAIL);

      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }

    @Test
    @DisplayName("PROCESSING → CANCELED on CANCEL")
    void processingToCanceledOnCancel() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.CANCEL);

      assertThat(testPayment.currentState()).isEqualTo(PaymentState.CANCELED);
    }

    @Test
    @DisplayName("Invalid: NEW cannot receive AUTHORIZE")
    void newCannotReceiveAuthorize() {
      assertThrows(InvalidTransitionException.class,
          () -> paymentService.processEvent(1L, PaymentEvent.AUTHORIZE));
    }

    @Test
    @DisplayName("Invalid: NEW cannot receive COMPLETE")
    void newCannotReceiveComplete() {
      assertThrows(InvalidTransitionException.class,
          () -> paymentService.processEvent(1L, PaymentEvent.COMPLETE));
    }

    @Test
    @DisplayName("Terminal state FAILED rejects all events")
    void failedRejectsAllEvents() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.FAIL);
      List.of(PaymentEvent.INITIATE, PaymentEvent.AUTHORIZE, PaymentEvent.COMPLETE,
          PaymentEvent.CANCEL, PaymentEvent.REFUND)
          .forEach(e -> assertThrows(InvalidTransitionException.class,
              () -> paymentService.processEvent(1L, e)));
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }
  }

  @Nested
  @DisplayName("Orthogonal Regions")
  class OrthogonalRegions {

    @Test
    @DisplayName("Authorization region transitions independently of FraudCheck")
    void authorizationRegionTransitionsIndependently() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_APPROVED,
          PaymentState.FRAUD_EVALUATING);
    }

    @Test
    @DisplayName("FraudCheck region transitions independently of Authorization")
    void fraudCheckRegionTransitionsIndependently() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_PENDING,
          PaymentState.FRAUD_PASSED);
    }

    @Test
    @DisplayName("Both regions can reach their success states simultaneously")
    void bothRegionsReachSuccessStates() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));
      paymentService.processEvent(1L, PaymentEvent.INITIATE);

      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);

      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_APPROVED,
          PaymentState.FRAUD_PASSED);
    }

    @Test
    @DisplayName("FRAUD_ALERT transitions FraudCheck region to FRAUD_DETECTED")
    void fraudAlertTransitionsToDetected() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_ALERT);

      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_PENDING,
          PaymentState.FRAUD_DETECTED);
    }
  }

  @Nested
  @DisplayName("Guards")
  class Guards {

    @Test
    @DisplayName("fraudCheckGuard allows transition when risk is LOW")
    void fraudCheckGuardAllowsLowRisk() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));

      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(10, LOW_STR, ALLOW_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_APPROVED);
    }

    @Test
    @DisplayName("fraudCheckGuard blocks transition when risk is HIGH → AUTH_REJECTED")
    void fraudCheckGuardBlocksHighRisk() {
      testPayment.setMoney(Money.of(TEN_THOUSAND, USD_STR));

      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(92, HIGH_STR, BLOCK_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_REJECTED);
    }

    @Test
    @DisplayName("fraudCheckGuard stores score in extended state")
    void fraudCheckGuardStoresScoreInExtendedState() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));

      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(25, LOW_STR, ALLOW_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

      assertThat(lastStateMachine.getExtendedState().get("fraudScore", Integer.class))
          .isEqualTo(25);
      assertThat(lastStateMachine.getExtendedState().get("fraudRisk", String.class))
          .isEqualTo(LOW_STR);
    }

    @Test
    @DisplayName("refundWindowGuard blocks refund outside 30-day window")
    void refundWindowGuardBlocks() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);


      paymentService.processEvent(1L, PaymentEvent.COMPLETE);
      testPayment.setCreatedAt(LocalDateTime.now().minusDays(60));

      paymentService.processEvent(1L, PaymentEvent.REFUND);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
    }

    @Test
    @DisplayName("refundWindowGuard allows refund within 30-day window")
    void refundWindowGuardAllows() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);


      paymentService.processEvent(1L, PaymentEvent.COMPLETE);
      testPayment.setCreatedAt(LocalDateTime.now().minusDays(5));

      paymentService.processEvent(1L, PaymentEvent.REFUND);

      assertThat(testPayment.currentState()).isEqualTo(PaymentState.REFUNDED);
    }
  }

  @Nested
  @DisplayName("Actions")
  class Actions {

    @Test
    @DisplayName("feeCalculationAction is invoked on AUTHORIZE transition")
    void feeCalculationActionInvokedOnAuthorize() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

      verify(feeCalculationService).calculate(Money.of(HUNDRED, USD_STR));
    }

    @Test
    @DisplayName("feeCalculationAction stores fee in extended state")
    void feeCalculationActionStoresFeeInExtendedState() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

      assertThat(lastStateMachine.getExtendedState().get("processingFee", BigDecimal.class))
          .isEqualByComparingTo("3.20");
      assertThat(lastStateMachine.getExtendedState().get("netAmount", BigDecimal.class))
          .isEqualByComparingTo("96.80");
    }

    @Test
    @DisplayName("feeCalculationAction is NOT invoked when fraud guard blocks")
    void feeCalculationActionNotInvokedWhenGuardBlocks() {
      testPayment.setMoney(Money.of(TEN_THOUSAND, USD_STR));

      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(92, HIGH_STR, BLOCK_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      verify(feeCalculationService, never()).calculate(any());
    }

    @Test
    @DisplayName("settlementAction is invoked on COMPLETE transition")
    void settlementActionInvokedOnComplete() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));



      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
      paymentService.processEvent(1L, PaymentEvent.COMPLETE);

      verify(feeCalculationService).saveSettlement(1L, Money.of(HUNDRED, USD_STR));
      verify(walletClient).debit(eq(1L), any(), eq(USD_STR));
      verify(ledgerPublisher).publishEvent(eq(1L), eq(new BigDecimal(HUNDRED)), any(), eq(USD_STR));
    }

    @Test
    @DisplayName("completedEntryAction fires when entering COMPLETED")
    void completedEntryActionFires() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));



      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
      paymentService.processEvent(1L, PaymentEvent.COMPLETE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
    }

    @Test
    @DisplayName("completedEntryAction is skipped when isRestoring=true")
    void completedEntryActionSkippedWhenRestoring() {
      testPayment.setMoney(Money.of(HUNDRED, USD_STR));
      testPayment.setState(PaymentState.COMPLETED.name());
      assertThrows(InvalidTransitionException.class,
          () -> paymentService.processEvent(1L, PaymentEvent.AUTHORIZE));
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
    }
  }

  @Nested
  @DisplayName("Full Lifecycle")
  class FullLifecycle {

    @Test
    @DisplayName("Happy path: NEW → PROCESSING → COMPLETED → REFUNDED")
    void happyPathToCompletedThenRefund() {
      testPayment.setMoney(Money.of("250.00", "EUR"));



      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      assertThat(List.of(testPayment.currentState())).contains(PaymentState.PROCESSING);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
      paymentService.processEvent(1L, PaymentEvent.COMPLETE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
      testPayment.setCreatedAt(LocalDateTime.now().minusDays(2));
      paymentService.processEvent(1L, PaymentEvent.REFUND);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.REFUNDED);
    }

    @Test
    @DisplayName("Fraud rejection path: NEW → PROCESSING → AUTH_REJECTED")
    void fraudRejectionPath() {
      testPayment.setMoney(Money.of(TEN_THOUSAND, USD_STR));

      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(92, HIGH_STR, BLOCK_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
      assertThat(lastStateMachine.getState().getIds()).contains(PaymentState.AUTH_REJECTED,
          PaymentState.FRAUD_EVALUATING);
      paymentService.processEvent(1L, PaymentEvent.FAIL);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }

    @Test
    @DisplayName("Cancellation path: NEW → PROCESSING → CANCELED")
    void cancellationPath() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.CANCEL);

      assertThat(testPayment.currentState()).isEqualTo(PaymentState.CANCELED);
    }
  }
}

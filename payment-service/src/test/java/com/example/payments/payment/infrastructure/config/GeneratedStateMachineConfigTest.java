package com.example.payments.payment.infrastructure.config;

import com.example.payments.sharedkernel.Money;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.payment.application.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.StateMachineBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.argThat;

class GeneratedStateMachineConfigTest {
  private static final String LOW_STR = "LOW";
  private static final String ALLOW_STR = "ALLOW";
  private static final String HUNDRED = "100.00";
  private static final String USD_STR = "USD";
  private static final String HIGH_STR = "HIGH";
  private static final String BLOCK_STR = "BLOCK";
  private static final String PENDING_REVIEW_STR = "PENDING_REVIEW";

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
        .thenReturn(new FeeBreakdown(Money.of(HUNDRED, USD_STR), Money.of("2.90", USD_STR),
            Money.of("0.30", USD_STR), Money.of("3.20", USD_STR), Money.of("96.80", USD_STR)));

    when(walletClient.debit(anyLong(), any(BigDecimal.class), anyString()))
        .thenReturn(DebitResponse.builder().status("SUCCESS").build());
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
    @DisplayName("NEW to RISK_CHECKING on INITIATE")
    void newToRiskCheckingOnInitiate() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.RISK_CHECKING);
    }

    @Test
    @DisplayName("RISK_CHECKING to FUNDS_RESERVING on Low Risk")
    void riskCheckingToFundsReservingOnLowRisk() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FUNDS_RESERVING);
    }

    @Test
    @DisplayName("RISK_CHECKING to REJECTED on High Risk")
    void riskCheckingToRejectedOnHighRisk() {
      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(95, HIGH_STR, BLOCK_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.REJECTED);
    }

    @Test
    @DisplayName("RISK_CHECKING to MANUAL_REVIEW on Medium Risk")
    void riskCheckingToManualReviewOnMediumRisk() {
      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(50, PENDING_REVIEW_STR, PENDING_REVIEW_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.MANUAL_REVIEW);
    }
  }

  @Nested
  @DisplayName("Manual Review and Saga Steps")
  class ManualReviewAndSagaSteps {

    @Test
    @DisplayName("MANUAL_REVIEW to FUNDS_RESERVING on ANALYST_APPROVE")
    void manualReviewToFundsReservingOnApprove() {
      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(50, PENDING_REVIEW_STR, PENDING_REVIEW_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.ANALYST_APPROVE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FUNDS_RESERVING);
    }

    @Test
    @DisplayName("MANUAL_REVIEW to REJECTED on ANALYST_REJECT")
    void manualReviewToRejectedOnReject() {
      when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
          .thenReturn(new FraudCheckPort.FraudResult(50, PENDING_REVIEW_STR, PENDING_REVIEW_STR));

      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.ANALYST_REJECT);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.REJECTED);
    }

    @Test
    @DisplayName("Saga flow to COMPLETED on happy path")
    void sagaFlowToCompletedOnHappyPath() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FUNDS_RESERVING);

      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FEE_RESERVING);

      paymentService.processEvent(1L, PaymentEvent.FEE_RESERVED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.ACCOUNTING_POSTING);

      paymentService.processEvent(1L, PaymentEvent.ENTRIES_POSTED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
    }
  }

  @Nested
  @DisplayName("Compensations and Rollbacks")
  class CompensationsAndRollbacks {

    @Test
    @DisplayName("FUNDS_RESERVING to FAILED on base amount reservation failure")
    void baseReservationFailureGoesToFailed() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FUNDS_RESERVING);

      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVATION_FAILED);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }

    @Test
    @DisplayName("FEE_RESERVING to COMPENSATING to FAILED on fee reservation failure")
    void feeReservationFailureCompensates() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVED);

      paymentService.processEvent(1L, PaymentEvent.FEE_RESERVATION_FAILED);
      verify(walletClient).debit(eq(1L), argThat(v -> v.compareTo(BigDecimal.ZERO) < 0),
          anyString());

      paymentService.processEvent(1L, PaymentEvent.COMPENSATION_COMPLETE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }

    @Test
    @DisplayName("ACCOUNTING_POSTING to COMPENSATING to FAILED on ledger posting failure")
    void ledgerPostingFailureCompensates() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVED);
      paymentService.processEvent(1L, PaymentEvent.FEE_RESERVED);

      paymentService.processEvent(1L, PaymentEvent.POSTING_FAILED);
      verify(walletClient, times(4)).debit(anyLong(), any(BigDecimal.class), anyString());

      paymentService.processEvent(1L, PaymentEvent.COMPENSATION_COMPLETE);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
    }
  }

  @Nested
  @DisplayName("Refund and Terminal States")
  class RefundAndTerminalStates {

    @Test
    @DisplayName("Refund allowed in 30-day window")
    void refundWithin30DaysAllowed() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVED);
      paymentService.processEvent(1L, PaymentEvent.FEE_RESERVED);
      paymentService.processEvent(1L, PaymentEvent.ENTRIES_POSTED);

      testPayment.setCreatedAt(LocalDateTime.now().minusDays(5));
      paymentService.processEvent(1L, PaymentEvent.REFUND);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.REFUNDED);
    }

    @Test
    @DisplayName("Refund blocked outside 30-day window")
    void refundOutside30DaysBlocked() {
      paymentService.processEvent(1L, PaymentEvent.INITIATE);
      paymentService.processEvent(1L, PaymentEvent.RISK_EVALUATED);
      paymentService.processEvent(1L, PaymentEvent.FUNDS_RESERVED);
      paymentService.processEvent(1L, PaymentEvent.FEE_RESERVED);
      paymentService.processEvent(1L, PaymentEvent.ENTRIES_POSTED);

      testPayment.setCreatedAt(LocalDateTime.now().minusDays(45));
      paymentService.processEvent(1L, PaymentEvent.REFUND);
      assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
    }
  }
}

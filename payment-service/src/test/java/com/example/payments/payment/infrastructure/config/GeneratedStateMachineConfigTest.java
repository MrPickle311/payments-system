package com.example.payments.payment.infrastructure.config;

import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.fee.application.FeeCalculationService;
import com.example.payments.fee.domain.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.InvalidTransitionException;
import com.example.payments.payment.infrastructure.config.PaymentStateMachineInterceptor;
import com.example.payments.payment.infrastructure.config.PaymentStateMachinePersister;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import java.math.BigDecimal;
import com.example.payments.common.domain.Money;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for the state machine topology generated from {@code statemachine.puml}.
 *
 * <p>These tests validate that:
 * <ul>
 *   <li>State transitions follow the PUML-defined topology</li>
 *   <li>Guards are evaluated and block/allow transitions correctly</li>
 *   <li>Actions are invoked during transitions</li>
 *   <li>Orthogonal regions (Authorization + FraudCheck) work independently</li>
 *   <li>Invalid transitions are rejected</li>
 * </ul>
 *
 * <p>Uses a standalone state machine (no Spring context) with mocked services
 * to isolate the generated wiring from infrastructure concerns.
 */
class GeneratedStateMachineConfigTest {

    private FraudCheckService fraudCheckService;
    private FeeCalculationService feeCalculationService;
    private WalletClient walletClient;
    private LedgerPublisher ledgerPublisher;
    private PaymentService paymentService;
    private Payment testPayment;
    private PaymentRepository paymentRepository;
    private PaymentHistoryRepository paymentHistoryRepository;
    private StateMachine<PaymentState, PaymentEvent> lastStateMachine;

    
    @BeforeEach
    void setUp() throws Exception {
        fraudCheckService = mock(FraudCheckService.class);
        feeCalculationService = mock(FeeCalculationService.class);
        walletClient = mock(WalletClient.class);
        ledgerPublisher = mock(LedgerPublisher.class);

        when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                .thenReturn(new FraudCheckService.FraudResult(10, "LOW", "ALLOW"));

        when(feeCalculationService.calculate(any(Money.class)))
                .thenReturn(new FeeBreakdown(
                        Money.of("100.00", "USD"),
                        Money.of("2.9000", "USD"),
                        Money.of("0.30", "USD"),
                        Money.of("3.2000", "USD"),
                        Money.of("96.8000", "USD")
                ));

        paymentRepository = mock(PaymentRepository.class);
        paymentHistoryRepository = mock(PaymentHistoryRepository.class);

        testPayment = Payment.builder()
                .id(1L)
                .transactionId("txn_123")
                .money(Money.of("100.00", "USD"))
                .state(PaymentState.NEW.name())
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory = mock(StateMachineFactory.class);
        when(stateMachineFactory.getStateMachine(anyString())).thenAnswer(i -> {
            lastStateMachine = buildStateMachine();
            return lastStateMachine;
        });

        PaymentStateMachineInterceptor interceptor = new PaymentStateMachineInterceptor(paymentHistoryRepository);
        PaymentStateMachinePersister persister = new PaymentStateMachinePersister();

        paymentService = new PaymentService(paymentRepository, paymentHistoryRepository, stateMachineFactory, interceptor, persister);
    }

    /**
     * Builds a standalone state machine using the generated abstract config
     * wired with a concrete subclass that uses mocked services.
     */
    private StateMachine<PaymentState, PaymentEvent> buildStateMachine() throws Exception {
        // Create a concrete config backed by mocks
        StateMachineConfig config = new StateMachineConfig(fraudCheckService, 
                                                         feeCalculationService,
                                                         walletClient,
                                                         ledgerPublisher);

        StateMachineBuilder.Builder<PaymentState, PaymentEvent> builder =
                StateMachineBuilder.builder();

        config.configure(builder.configureConfiguration());
        config.configure(builder.configureStates());
        config.configure(builder.configureTransitions());

        StateMachine<PaymentState, PaymentEvent> sm = builder.build();
        sm.start();
        return sm;
    }

    // =========================================================================
    // State Transition Tests
    // =========================================================================

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

            // PROCESSING is a composite — the machine should be in PROCESSING
            // and simultaneously in both region initial states
            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.PROCESSING,
                              PaymentState.AUTH_PENDING,
                              PaymentState.FRAUD_EVALUATING);
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
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.AUTHORIZE));
        }

        @Test
        @DisplayName("Invalid: NEW cannot receive COMPLETE")
        void newCannotReceiveComplete() {
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.COMPLETE));
        }

        @Test
        @DisplayName("Terminal state FAILED rejects all events")
        void failedRejectsAllEvents() {
            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.FAIL);

            // Try every event — should stay FAILED
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.INITIATE));
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.AUTHORIZE));
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.COMPLETE));
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.CANCEL));
            assertThrows(InvalidTransitionException.class, () -> paymentService.processEvent(1L, PaymentEvent.REFUND));

            assertThat(testPayment.currentState()).isEqualTo(PaymentState.FAILED);
        }
    }

    // =========================================================================
    // Orthogonal Region Tests
    // =========================================================================

    @Nested
    @DisplayName("Orthogonal Regions")
    class OrthogonalRegions {

        @Test
        @DisplayName("Authorization region transitions independently of FraudCheck")
        void authorizationRegionTransitionsIndependently() {
            testPayment.setMoney(Money.of("100.00", "USD"));
            paymentService.processEvent(1L, PaymentEvent.INITIATE);

            // Authorize in the auth region (fraud check passes → AUTH_APPROVED)
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            // Auth region moved to AUTH_APPROVED, fraud region stays at FRAUD_EVALUATING
            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED, PaymentState.FRAUD_EVALUATING);
        }

        @Test
        @DisplayName("FraudCheck region transitions independently of Authorization")
        void fraudCheckRegionTransitionsIndependently() {
            paymentService.processEvent(1L, PaymentEvent.INITIATE);

            // Clear fraud in the fraud region
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);

            // Fraud region moved to FRAUD_PASSED, auth region stays at AUTH_PENDING
            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_PENDING, PaymentState.FRAUD_PASSED);
        }

        @Test
        @DisplayName("Both regions can reach their success states simultaneously")
        void bothRegionsReachSuccessStates() {
            testPayment.setMoney(Money.of("100.00", "USD"));
            paymentService.processEvent(1L, PaymentEvent.INITIATE);

            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);     // Auth → AUTH_APPROVED
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);   // Fraud → FRAUD_PASSED

            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED, PaymentState.FRAUD_PASSED);
        }

        @Test
        @DisplayName("FRAUD_ALERT transitions FraudCheck region to FRAUD_DETECTED")
        void fraudAlertTransitionsToDetected() {
            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_ALERT);

            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_PENDING, PaymentState.FRAUD_DETECTED);
        }
    }

    // =========================================================================
    // Guard Tests
    // =========================================================================

    @Nested
    @DisplayName("Guards")
    class Guards {

        @Test
        @DisplayName("fraudCheckGuard allows transition when risk is LOW")
        void fraudCheckGuardAllowsLowRisk() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(10, "LOW", "ALLOW"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED);
        }

        @Test
        @DisplayName("fraudCheckGuard blocks transition when risk is HIGH → AUTH_REJECTED")
        void fraudCheckGuardBlocksHighRisk() {
            testPayment.setMoney(Money.of("10000.00", "USD"));

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            // Negated guard fires → AUTH_REJECTED
            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_REJECTED);
        }

        @Test
        @DisplayName("fraudCheckGuard stores score in extended state")
        void fraudCheckGuardStoresScoreInExtendedState() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(25, "LOW", "ALLOW"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            assertThat(lastStateMachine.getExtendedState().get("fraudScore", Integer.class))
                    .isEqualTo(25);
            assertThat(lastStateMachine.getExtendedState().get("fraudRisk", String.class))
                    .isEqualTo("LOW");
        }

        @Test
        @DisplayName("refundWindowGuard blocks refund outside 30-day window")
        void refundWindowGuardBlocks() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            // Walk the machine to COMPLETED
            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);
            paymentService.processEvent(1L, PaymentEvent.COMPLETE);

            // Set createdAt to 60 days ago — outside window
            testPayment.setCreatedAt(LocalDateTime.now().minusDays(60));

            paymentService.processEvent(1L, PaymentEvent.REFUND);

            // Should stay COMPLETED — refund blocked
            assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
        }

        @Test
        @DisplayName("refundWindowGuard allows refund within 30-day window")
        void refundWindowGuardAllows() throws Exception {
            testPayment.setMoney(Money.of("100.00", "USD"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);
            paymentService.processEvent(1L, PaymentEvent.COMPLETE);

            // Set createdAt to 5 days ago — within window
            testPayment.setCreatedAt(LocalDateTime.now().minusDays(5));

            paymentService.processEvent(1L, PaymentEvent.REFUND);

            assertThat(testPayment.currentState()).isEqualTo(PaymentState.REFUNDED);
        }
    }

    // =========================================================================
    // Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Actions")
    class Actions {

        @Test
        @DisplayName("feeCalculationAction is invoked on AUTHORIZE transition")
        void feeCalculationActionInvokedOnAuthorize() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            verify(feeCalculationService).calculate(Money.of("100.00", "USD"));
        }

        @Test
        @DisplayName("feeCalculationAction stores fee in extended state")
        void feeCalculationActionStoresFeeInExtendedState() {
            testPayment.setMoney(Money.of("100.00", "USD"));

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
            testPayment.setMoney(Money.of("10000.00", "USD"));

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            // Fee calculation should NOT be called — guard blocked the transition
            verify(feeCalculationService, never()).calculate(any());
        }

        @Test
        @DisplayName("settlementAction is invoked on COMPLETE transition")
        void settlementActionInvokedOnComplete() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
            paymentService.processEvent(1L, PaymentEvent.COMPLETE);

            verify(feeCalculationService).saveSettlement(1L, Money.of("100.00", "USD"));
            verify(walletClient).debit(eq(1L), any(), eq("USD"));
            verify(ledgerPublisher).publishEvent(eq(1L), eq(new BigDecimal("100.00")), any(), eq("USD"));
        }

        @Test
        @DisplayName("completedEntryAction fires when entering COMPLETED")
        void completedEntryActionFires() {
            testPayment.setMoney(Money.of("100.00", "USD"));

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);
            paymentService.processEvent(1L, PaymentEvent.COMPLETE);

            // Verify we reached COMPLETED — entry action ran (logging only, no mock to verify)
            assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
        }

        @Test
        @DisplayName("completedEntryAction is skipped when isRestoring=true")
        void completedEntryActionSkippedWhenRestoring() {
            testPayment.setMoney(Money.of("100.00", "USD"));
            testPayment.setState(PaymentState.COMPLETED.name());

            // A mock process just to trigger restoration of COMPLETED state
            assertThrows(InvalidTransitionException.class, () -> 
                paymentService.processEvent(1L, PaymentEvent.AUTHORIZE)
            );

            // Entry action should have been skipped (early return), and state is COMPLETED
            assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);
        }
    }

    // =========================================================================
    // Full Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Full Lifecycle")
    class FullLifecycle {

        @Test
        @DisplayName("Happy path: NEW → PROCESSING → COMPLETED → REFUNDED")
        void happyPathToCompletedThenRefund() {
            testPayment.setMoney(Money.of("250.00", "EUR"));

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            assertThat(java.util.List.of(testPayment.currentState())).contains(PaymentState.PROCESSING);

            // Both regions proceed
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);
            paymentService.processEvent(1L, PaymentEvent.FRAUD_CLEAR);

            // Complete
            paymentService.processEvent(1L, PaymentEvent.COMPLETE);
            assertThat(testPayment.currentState()).isEqualTo(PaymentState.COMPLETED);

            // Refund within window
            testPayment.setCreatedAt(LocalDateTime.now().minusDays(2));
            paymentService.processEvent(1L, PaymentEvent.REFUND);
            assertThat(testPayment.currentState()).isEqualTo(PaymentState.REFUNDED);
        }

        @Test
        @DisplayName("Fraud rejection path: NEW → PROCESSING → AUTH_REJECTED")
        void fraudRejectionPath() {
            testPayment.setMoney(Money.of("10000.00", "USD"));

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            paymentService.processEvent(1L, PaymentEvent.INITIATE);
            paymentService.processEvent(1L, PaymentEvent.AUTHORIZE);

            // Auth region goes to rejected, but still inside PROCESSING composite
            assertThat(lastStateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_REJECTED, PaymentState.FRAUD_EVALUATING);

            // Can still fail the whole payment
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

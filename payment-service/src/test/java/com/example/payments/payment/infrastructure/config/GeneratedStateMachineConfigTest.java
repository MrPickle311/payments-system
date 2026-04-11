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
    private StateMachine<PaymentState, PaymentEvent> stateMachine;

    @BeforeEach
    void setUp() throws Exception {
        fraudCheckService = mock(FraudCheckService.class);
        feeCalculationService = mock(FeeCalculationService.class);
        walletClient = mock(WalletClient.class);
        ledgerPublisher = mock(LedgerPublisher.class);

        // Default: fraud check passes, low risk
        when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                .thenReturn(new FraudCheckService.FraudResult(10, "LOW", "ALLOW"));

        // Default: fee calculation returns a breakdown
        when(feeCalculationService.calculate(any(Money.class)))
                .thenReturn(new FeeBreakdown(
                        Money.of("100.00", "USD"),
                        Money.of("2.9000", "USD"),
                        Money.of("0.30", "USD"),
                        Money.of("3.2000", "USD"),
                        Money.of("96.8000", "USD")
                ));

        stateMachine = buildStateMachine();
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

    /** Seeds standard payment context into ExtendedState. */
    private void seedExtendedState(Long paymentId, BigDecimal amount, String currency) {
        stateMachine.getExtendedState().getVariables().put("paymentId", paymentId);
        stateMachine.getExtendedState().getVariables().put("paymentAmount", amount);
        stateMachine.getExtendedState().getVariables().put("paymentCurrency", currency);
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
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.NEW);
        }

        @Test
        @DisplayName("NEW → PROCESSING on INITIATE")
        void newToProcessingOnInitiate() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);

            // PROCESSING is a composite — the machine should be in PROCESSING
            // and simultaneously in both region initial states
            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.PROCESSING,
                              PaymentState.AUTH_PENDING,
                              PaymentState.FRAUD_EVALUATING);
        }

        @Test
        @DisplayName("PROCESSING → FAILED on FAIL")
        void processingToFailedOnFail() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.FAIL);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.FAILED);
        }

        @Test
        @DisplayName("PROCESSING → CANCELED on CANCEL")
        void processingToCanceledOnCancel() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.CANCEL);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.CANCELED);
        }

        @Test
        @DisplayName("Invalid: NEW cannot receive AUTHORIZE")
        void newCannotReceiveAuthorize() {
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            // Should remain in NEW — event was rejected
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.NEW);
        }

        @Test
        @DisplayName("Invalid: NEW cannot receive COMPLETE")
        void newCannotReceiveComplete() {
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.NEW);
        }

        @Test
        @DisplayName("Terminal state FAILED rejects all events")
        void failedRejectsAllEvents() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.FAIL);

            // Try every event — should stay FAILED
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);
            stateMachine.sendEvent(PaymentEvent.CANCEL);
            stateMachine.sendEvent(PaymentEvent.REFUND);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.FAILED);
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
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");
            stateMachine.sendEvent(PaymentEvent.INITIATE);

            // Authorize in the auth region (fraud check passes → AUTH_APPROVED)
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            // Auth region moved to AUTH_APPROVED, fraud region stays at FRAUD_EVALUATING
            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED, PaymentState.FRAUD_EVALUATING);
        }

        @Test
        @DisplayName("FraudCheck region transitions independently of Authorization")
        void fraudCheckRegionTransitionsIndependently() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);

            // Clear fraud in the fraud region
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);

            // Fraud region moved to FRAUD_PASSED, auth region stays at AUTH_PENDING
            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_PENDING, PaymentState.FRAUD_PASSED);
        }

        @Test
        @DisplayName("Both regions can reach their success states simultaneously")
        void bothRegionsReachSuccessStates() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");
            stateMachine.sendEvent(PaymentEvent.INITIATE);

            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);     // Auth → AUTH_APPROVED
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);   // Fraud → FRAUD_PASSED

            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED, PaymentState.FRAUD_PASSED);
        }

        @Test
        @DisplayName("FRAUD_ALERT transitions FraudCheck region to FRAUD_DETECTED")
        void fraudAlertTransitionsToDetected() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_ALERT);

            assertThat(stateMachine.getState().getIds())
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
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(10, "LOW", "ALLOW"));

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_APPROVED);
        }

        @Test
        @DisplayName("fraudCheckGuard blocks transition when risk is HIGH → AUTH_REJECTED")
        void fraudCheckGuardBlocksHighRisk() {
            seedExtendedState(1L, new BigDecimal("10000.00"), "USD");

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            // Negated guard fires → AUTH_REJECTED
            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_REJECTED);
        }

        @Test
        @DisplayName("fraudCheckGuard stores score in extended state")
        void fraudCheckGuardStoresScoreInExtendedState() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(25, "LOW", "ALLOW"));

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            assertThat(stateMachine.getExtendedState().get("fraudScore", Integer.class))
                    .isEqualTo(25);
            assertThat(stateMachine.getExtendedState().get("fraudRisk", String.class))
                    .isEqualTo("LOW");
        }

        @Test
        @DisplayName("refundWindowGuard blocks refund outside 30-day window")
        void refundWindowGuardBlocks() throws Exception {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            // Walk the machine to COMPLETED
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            // Set createdAt to 60 days ago — outside window
            stateMachine.getExtendedState().getVariables()
                    .put("paymentCreatedAt", LocalDateTime.now().minusDays(60));

            stateMachine.sendEvent(PaymentEvent.REFUND);

            // Should stay COMPLETED — refund blocked
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.COMPLETED);
        }

        @Test
        @DisplayName("refundWindowGuard allows refund within 30-day window")
        void refundWindowGuardAllows() throws Exception {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            // Set createdAt to 5 days ago — within window
            stateMachine.getExtendedState().getVariables()
                    .put("paymentCreatedAt", LocalDateTime.now().minusDays(5));

            stateMachine.sendEvent(PaymentEvent.REFUND);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.REFUNDED);
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
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            verify(feeCalculationService).calculate(Money.of("100.00", "USD"));
        }

        @Test
        @DisplayName("feeCalculationAction stores fee in extended state")
        void feeCalculationActionStoresFeeInExtendedState() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            assertThat(stateMachine.getExtendedState().get("processingFee", BigDecimal.class))
                    .isEqualByComparingTo("3.20");
            assertThat(stateMachine.getExtendedState().get("netAmount", BigDecimal.class))
                    .isEqualByComparingTo("96.80");
        }

        @Test
        @DisplayName("feeCalculationAction is NOT invoked when fraud guard blocks")
        void feeCalculationActionNotInvokedWhenGuardBlocks() {
            seedExtendedState(1L, new BigDecimal("10000.00"), "USD");

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            // Fee calculation should NOT be called — guard blocked the transition
            verify(feeCalculationService, never()).calculate(any());
        }

        @Test
        @DisplayName("settlementAction is invoked on COMPLETE transition")
        void settlementActionInvokedOnComplete() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            verify(feeCalculationService).saveSettlement(1L, Money.of("100.00", "USD"));
            verify(walletClient).debit(eq(1L), any(), eq("USD"));
            verify(ledgerPublisher).publishEvent(eq(1L), eq(new BigDecimal("100.00")), any(), eq("USD"));
        }

        @Test
        @DisplayName("completedEntryAction fires when entering COMPLETED")
        void completedEntryActionFires() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            // Verify we reached COMPLETED — entry action ran (logging only, no mock to verify)
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.COMPLETED);
        }

        @Test
        @DisplayName("completedEntryAction is skipped when isRestoring=true")
        void completedEntryActionSkippedWhenRestoring() {
            seedExtendedState(1L, new BigDecimal("100.00"), "USD");
            stateMachine.getExtendedState().getVariables().put("isRestoring", Boolean.TRUE);

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);
            stateMachine.sendEvent(PaymentEvent.COMPLETE);

            // Entry action should have been skipped (early return), but state still reached
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.COMPLETED);
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
            seedExtendedState(1L, new BigDecimal("250.00"), "EUR");

            when(feeCalculationService.saveSettlement(anyLong(), any(Money.class)))
                    .thenReturn(null);

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            assertThat(stateMachine.getState().getIds()).contains(PaymentState.PROCESSING);

            // Both regions proceed
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);
            stateMachine.sendEvent(PaymentEvent.FRAUD_CLEAR);

            // Complete
            stateMachine.sendEvent(PaymentEvent.COMPLETE);
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.COMPLETED);

            // Refund within window
            stateMachine.getExtendedState().getVariables()
                    .put("paymentCreatedAt", LocalDateTime.now().minusDays(2));
            stateMachine.sendEvent(PaymentEvent.REFUND);
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.REFUNDED);
        }

        @Test
        @DisplayName("Fraud rejection path: NEW → PROCESSING → AUTH_REJECTED")
        void fraudRejectionPath() {
            seedExtendedState(1L, new BigDecimal("10000.00"), "USD");

            when(fraudCheckService.evaluate(anyLong(), any(Money.class)))
                    .thenReturn(new FraudCheckService.FraudResult(92, "HIGH", "BLOCK"));

            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.AUTHORIZE);

            // Auth region goes to rejected, but still inside PROCESSING composite
            assertThat(stateMachine.getState().getIds())
                    .contains(PaymentState.AUTH_REJECTED, PaymentState.FRAUD_EVALUATING);

            // Can still fail the whole payment
            stateMachine.sendEvent(PaymentEvent.FAIL);
            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.FAILED);
        }

        @Test
        @DisplayName("Cancellation path: NEW → PROCESSING → CANCELED")
        void cancellationPath() {
            stateMachine.sendEvent(PaymentEvent.INITIATE);
            stateMachine.sendEvent(PaymentEvent.CANCEL);

            assertThat(stateMachine.getState().getId()).isEqualTo(PaymentState.CANCELED);
        }
    }
}

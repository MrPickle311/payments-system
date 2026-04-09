package com.example.payments.config;

import com.example.payments.enums.PaymentEvent;
import com.example.payments.enums.PaymentState;
import com.example.payments.service.FeeCalculationService;
import com.example.payments.service.FraudCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Complete state machine topology for the Payment domain.
 *
 * <h2>Where business logic lives in a state machine — the four integration points</h2>
 *
 * <pre>
 *
 *  ┌──────────────────────────────────────────────────────────────────────────────┐
 *  │  Event arrives (e.g. AUTHORIZE)                                              │
 *  │                                                                              │
 *  │  1. GUARD evaluated ──► false → event DENIED, transition aborted            │
 *  │       └─ fraudCheckGuard: calls external fraud API, stores score in ExtState │
 *  │                                                                              │
 *  │  2. EXIT action of source state (none configured here)                       │
 *  │                                                                              │
 *  │  3. TRANSITION ACTION executes                                               │
 *  │       └─ feeCalculationAction: computes fee, stores in ExtState              │
 *  │       └─ settlementAction    : persists PaymentFee to DB, notifies accounting│
 *  │                                                                              │
 *  │  4. ENTRY action of target state executes                                    │
 *  │       └─ completedEntryAction: unlock product, queue invoice                 │
 *  │                                                                              │
 *  │  5. ExtendedState carries data across all steps within the request:          │
 *  │       paymentId, paymentAmount, paymentCurrency, paymentCreatedAt            │
 *  │       fraudScore, fraudRisk, processingFee, netAmount                        │
 *  └──────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class StateMachineConfig extends StateMachineConfigurerAdapter<PaymentState, PaymentEvent> {

    private final FraudCheckService fraudCheckService;
    private final FeeCalculationService feeCalculationService;

    // =========================================================================
    // Global machine configuration
    // =========================================================================

    @Override
    public void configure(StateMachineConfigurationConfigurer<PaymentState, PaymentEvent> config)
            throws Exception {
        config.withConfiguration()
                .autoStartup(false);
    }

    // =========================================================================
    // State registry
    // =========================================================================

    @Override
    public void configure(StateMachineStateConfigurer<PaymentState, PaymentEvent> states)
            throws Exception {
        states.withStates()
                .initial(PaymentState.NEW)
                .states(EnumSet.allOf(PaymentState.class))
                // Entry action: fires each time COMPLETED is entered via a real transition.
                // The isRestoring guard prevents re-firing on DB restore.
                .stateEntry(PaymentState.COMPLETED, completedEntryAction());
    }

    // =========================================================================
    // Transition table (with guards and actions attached)
    // =========================================================================

    @Override
    public void configure(StateMachineTransitionConfigurer<PaymentState, PaymentEvent> transitions)
            throws Exception {
        transitions

                // ── NEW → PENDING ──────────────────────────────────────────────
                .withExternal()
                    .source(PaymentState.NEW).target(PaymentState.PENDING)
                    .event(PaymentEvent.INITIATE)
                    .and()

                // ── PENDING → PENDING (3-D Secure redirect, self-transition) ───
                .withExternal()
                    .source(PaymentState.PENDING).target(PaymentState.PENDING)
                    .event(PaymentEvent.REDIRECT)
                    .and()

                // ── PENDING → AUTHORIZED ───────────────────────────────────────
                // Guard:  fraudCheckGuard  — calls external fraud API; DENIES if HIGH risk.
                // Action: feeCalculationAction — computes processing fee; stores in ExtState.
                .withExternal()
                    .source(PaymentState.PENDING).target(PaymentState.AUTHORIZED)
                    .event(PaymentEvent.AUTHORIZE)
                    .guard(fraudCheckGuard())
                    .action(feeCalculationAction(), feeCalculationErrorAction())
                    .and()

                // ── AUTHORIZED → COMPLETED ─────────────────────────────────────
                // Action: settlementAction — persists PaymentFee to DB; notifies accounting.
                .withExternal()
                    .source(PaymentState.AUTHORIZED).target(PaymentState.COMPLETED)
                    .event(PaymentEvent.COMPLETE)
                    .action(settlementAction(), settlementErrorAction())
                    .and()

                // ── Failures ───────────────────────────────────────────────────
                .withExternal()
                    .source(PaymentState.PENDING).target(PaymentState.FAILED)
                    .event(PaymentEvent.FAIL)
                    .and()
                .withExternal()
                    .source(PaymentState.AUTHORIZED).target(PaymentState.FAILED)
                    .event(PaymentEvent.FAIL)
                    .and()

                // ── Cancellations ──────────────────────────────────────────────
                .withExternal()
                    .source(PaymentState.PENDING).target(PaymentState.CANCELED)
                    .event(PaymentEvent.CANCEL)
                    .and()
                .withExternal()
                    .source(PaymentState.AUTHORIZED).target(PaymentState.CANCELED)
                    .event(PaymentEvent.CANCEL)
                    .and()

                // ── Refunds ────────────────────────────────────────────────────
                // Guard: refundWindowGuard — blocks refunds outside the 30-day window.
                .withExternal()
                    .source(PaymentState.AUTHORIZED).target(PaymentState.REFUNDED)
                    .event(PaymentEvent.REFUND)
                    .guard(refundWindowGuard())
                    .and()
                .withExternal()
                    .source(PaymentState.COMPLETED).target(PaymentState.REFUNDED)
                    .event(PaymentEvent.REFUND)
                    .guard(refundWindowGuard());
    }

    // =========================================================================
    // GUARDS — return false to deny the transition
    // =========================================================================

    /**
     * Calls the external fraud API before authorising a payment.
     *
     * <p>Stores {@code fraudScore} and {@code fraudRisk} in extended state so that
     * error messages and the audit trail can reference the exact score.
     *
     * <p>If the score is above the HIGH-risk threshold the transition is denied.
     * {@link com.example.payments.service.PaymentService} detects the DENIED result
     * and automatically sends a {@link PaymentEvent#FAIL} event so the payment moves
     * to FAILED rather than staying stuck in PENDING.
     */
    @Bean
    public Guard<PaymentState, PaymentEvent> fraudCheckGuard() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);

            FraudCheckService.FraudResult result =
                    fraudCheckService.evaluate(paymentId, amount, currency);

            // Stash the score so downstream code (service layer, error messages) can read it.
            context.getExtendedState().getVariables().put("fraudScore", result.score());
            context.getExtendedState().getVariables().put("fraudRisk", result.riskLevel());

            if (result.isHighRisk()) {
                log.warn("[Guard:Fraud] BLOCKED payment={} score={} risk={}",
                        paymentId, result.score(), result.riskLevel());
                return false;   // ← DENY the transition
            }

            log.info("[Guard:Fraud] ALLOWED payment={} score={} risk={}",
                    paymentId, result.score(), result.riskLevel());
            return true;
        };
    }

    /**
     * Enforces the 30-day refund window policy.
     *
     * <p>{@code paymentCreatedAt} is loaded into extended state by the persister
     * when the machine is restored, so this guard has no DB dependency of its own.
     */
    @Bean
    public Guard<PaymentState, PaymentEvent> refundWindowGuard() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            LocalDateTime createdAt =
                    context.getExtendedState().get("paymentCreatedAt", LocalDateTime.class);

            if (createdAt == null) {
                // Fail open: if we cannot determine age, allow the refund and let
                // downstream systems make the final call.
                log.warn("[Guard:Refund] createdAt missing for payment {} — allowing refund", paymentId);
                return true;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            boolean inWindow = createdAt.isAfter(cutoff);

            if (!inWindow) {
                log.warn("[Guard:Refund] BLOCKED payment={} — created={} is outside 30-day window",
                        paymentId, createdAt);
            } else {
                log.info("[Guard:Refund] ALLOWED payment={} — created={} is within refund window",
                        paymentId, createdAt);
            }
            return inWindow;
        };
    }

    // =========================================================================
    // TRANSITION ACTIONS — run during a specific source → target move
    // =========================================================================

    /**
     * Runs when PENDING → AUTHORIZED.
     *
     * <p>Computes the processing fee (2.9% + $0.30) from the payment amount stored
     * in extended state.  The result is stored back in extended state so that the
     * subsequent {@link #settlementAction()} can persist it without recalculating.
     *
     * <p>No DB write here — fees are only persisted after capture (COMPLETE event).
     */
    @Bean
    public Action<PaymentState, PaymentEvent> feeCalculationAction() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);

            FeeCalculationService.FeeBreakdown breakdown = feeCalculationService.calculate(amount);

            // Store in ExtendedState — settlementAction reads these on the COMPLETE event.
            context.getExtendedState().getVariables()
                    .put("processingFee", breakdown.totalFee());
            context.getExtendedState().getVariables()
                    .put("netAmount", breakdown.netAmount());

            log.info("[Action:FeeCalc] payment={} | gross={} | fee={} ({}% + {} flat) | net={}",
                    paymentId,
                    amount,
                    breakdown.totalFee(),
                    "2.9",
                    breakdown.flatFee(),
                    breakdown.netAmount());
        };
    }

    /** Error handler for {@link #feeCalculationAction()}. */
    @Bean
    public Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
        return context -> log.error("[Action:FeeCalc] ERROR during fee calculation for payment={}: {}",
                context.getExtendedState().get("paymentId", Long.class),
                context.getException() != null ? context.getException().getMessage() : "unknown");
    }

    /**
     * Runs when AUTHORIZED → COMPLETED.
     *
     * <p>This is the I/O-heavy step:
     * <ol>
     *   <li>Reads the pre-calculated fee from extended state.</li>
     *   <li>Persists a {@link com.example.payments.entity.PaymentFee} record to the DB
     *       (inside the same transaction as the state update).</li>
     *   <li>Simulates notifying an internal accounting service.</li>
     *   <li>Simulates generating a settlement report.</li>
     * </ol>
     */
    @Bean
    public Action<PaymentState, PaymentEvent> settlementAction() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount   = context.getExtendedState().get("paymentAmount",   BigDecimal.class);
            String currency     = context.getExtendedState().get("paymentCurrency", String.class);
            BigDecimal fee      = context.getExtendedState().get("processingFee",   BigDecimal.class);
            BigDecimal net      = context.getExtendedState().get("netAmount",        BigDecimal.class);

            // ── I/O operation 1: persist fee record to the database ────────
            feeCalculationService.saveSettlement(paymentId, amount, currency);

            // ── I/O operation 2: simulate notifying the accounting system ──
            log.info("[Action:Settlement] → POST /internal/accounting | payment={} net={} {}",
                    paymentId, net, currency);
            simulateInternalApiCall(50);
            log.info("[Action:Settlement] ← Accounting system acknowledged payment {}", paymentId);

            // ── I/O operation 3: simulate generating a settlement report ───
            log.info("[Action:Settlement] Generating settlement report for payment {}:", paymentId);
            log.info("  Gross amount    : {} {}", amount, currency);
            log.info("  Processing fee  : {} (2.9% + $0.30)", fee);
            log.info("  Net to merchant : {} {}", net, currency);
        };
    }

    /** Error handler for {@link #settlementAction()}. */
    @Bean
    public Action<PaymentState, PaymentEvent> settlementErrorAction() {
        return context -> log.error("[Action:Settlement] ERROR during settlement for payment={}: {}",
                context.getExtendedState().get("paymentId", Long.class),
                context.getException() != null ? context.getException().getMessage() : "unknown");
    }

    // =========================================================================
    // ENTRY ACTION — fires every time a state is entered
    // =========================================================================

    /**
     * Entry action for {@link PaymentState#COMPLETED}.
     *
     * <p>Distinct from {@link #settlementAction()}: the transition action handles
     * financial calculations; this entry action handles fulfilment notifications.
     * The separation means if COMPLETED is ever reachable from a second path in
     * a future version, notifications still fire automatically.
     *
     * <p>The {@code isRestoring} flag (set by the persister) prevents this from
     * re-firing when the machine is restored from the DB for an already-COMPLETED payment.
     */
    @Bean
    public Action<PaymentState, PaymentEvent> completedEntryAction() {
        return context -> {
            if (Boolean.TRUE.equals(context.getExtendedState().get("isRestoring", Boolean.class))) {
                log.debug("[Entry:Completed] Skipped — restoring from DB");
                return;
            }

            Long paymentId   = context.getExtendedState().get("paymentId",   Long.class);
            BigDecimal net   = context.getExtendedState().get("netAmount",    BigDecimal.class);
            String currency  = context.getExtendedState().get("paymentCurrency", String.class);

            log.info("========================================================");
            log.info("[Entry:Completed] Payment {} has been captured!", paymentId);
            log.info("[Entry:Completed] → Unlocking digital product for payment {}", paymentId);
            log.info("[Entry:Completed] → Queuing invoice email | net={} {}", net, currency);
            log.info("========================================================");
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void simulateInternalApiCall(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

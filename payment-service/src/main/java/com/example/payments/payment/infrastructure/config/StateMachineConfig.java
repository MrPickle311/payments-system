package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationService;
import com.example.payments.fee.domain.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckService;
import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import com.example.payments.common.domain.Money;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Concrete state machine configuration for the Payment domain.
 *
 * <p>Extends {@link GeneratedStateMachineConfig} which provides the full
 * state machine topology (states, transitions, regions) generated from
 * {@code statemachine.puml}. This class supplies only the business logic:
 * guards and actions.
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

@Configuration
@EnableStateMachineFactory

public class StateMachineConfig extends GeneratedStateMachineConfig {

    public StateMachineConfig(FraudCheckService fraudCheckService, 
                              FeeCalculationService feeCalculationService,
                              WalletClient walletClient,
                              LedgerPublisher ledgerPublisher) {
        this.fraudCheckService = fraudCheckService;
        this.feeCalculationService = feeCalculationService;
        this.walletClient = walletClient;
        this.ledgerPublisher = ledgerPublisher;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateMachineConfig.class);

    private final FraudCheckService fraudCheckService;
    private final FeeCalculationService feeCalculationService;
    private final WalletClient walletClient;
    private final LedgerPublisher ledgerPublisher;

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
     * {@link com.example.payments.payment.application.PaymentService} detects the DENIED result
     * and automatically sends a {@link PaymentEvent#FAIL} event so the payment moves
     * to FAILED rather than staying stuck in PENDING.
     */
    @Override
    protected Guard<PaymentState, PaymentEvent> fraudCheckGuard() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);
            Money money = Money.of(amount, currency);

            FraudCheckService.FraudResult result =
                    fraudCheckService.evaluate(paymentId, money);

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
    @Override
    protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
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
    @Override
    protected Action<PaymentState, PaymentEvent> feeCalculationAction() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);
            Money money = Money.of(amount, currency);

            FeeBreakdown breakdown = feeCalculationService.calculate(money);

            // Store in ExtendedState — settlementAction reads these on the COMPLETE event.
            context.getExtendedState().getVariables()
                    .put("processingFee", breakdown.totalFee().getAmount());
            context.getExtendedState().getVariables()
                    .put("netAmount", breakdown.netAmount().getAmount());

            log.info("[Action:FeeCalc] payment={} | gross={} | fee={} ({}% + {} flat) | net={}",
                    paymentId,
                    amount,
                    breakdown.totalFee().getAmount(),
                    "2.9",
                    breakdown.flatFee().getAmount(),
                    breakdown.netAmount().getAmount());
        };
    }

    /** Error handler for {@link #feeCalculationAction()}. */
    @Override
    protected Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
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
     *   <li>Persists a {@link com.example.payments.fee.domain.PaymentFee} record to the DB
     *       (inside the same transaction as the state update).</li>
     *   <li>Simulates notifying an internal accounting service.</li>
     *   <li>Simulates generating a settlement report.</li>
     * </ol>
     */
    @Override
    protected Action<PaymentState, PaymentEvent> settlementAction() {
        return context -> {
            Long paymentId = context.getExtendedState().get("paymentId", Long.class);
            BigDecimal amount   = context.getExtendedState().get("paymentAmount",   BigDecimal.class);
            String currency     = context.getExtendedState().get("paymentCurrency", String.class);
            BigDecimal fee      = context.getExtendedState().get("processingFee",   BigDecimal.class);
            BigDecimal net      = context.getExtendedState().get("netAmount",        BigDecimal.class);

            // ── I/O operation 1: persist fee record to the database ────────
            feeCalculationService.saveSettlement(paymentId, Money.of(amount, currency));

            // ── I/O operation 2: notify the Wallet service (REST) ──────────
            walletClient.debit(paymentId, net, currency);

            // ── I/O operation 3: notify the Ledger service (Kafka) ─────────
            ledgerPublisher.publishEvent(paymentId, amount, net, currency);

            // ── I/O operation 4: simulate notifying the accounting system ──
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
    @Override
    protected Action<PaymentState, PaymentEvent> settlementErrorAction() {
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
    @Override
    protected Action<PaymentState, PaymentEvent> completedEntryAction() {
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

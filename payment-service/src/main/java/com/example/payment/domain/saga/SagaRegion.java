package com.example.payment.domain.saga;

import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_FAILED;
import static com.example.payment.domain.enums.PaymentState.AUTH_PENDING;
import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.FEE_CALCULATED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATION_SKIPPED;
import static com.example.payment.domain.enums.PaymentState.FEE_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FEE_EVALUATING_COMPENSATION;
import static com.example.payment.domain.enums.PaymentState.FEE_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FRAUD_FAILED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFICATION_FAILED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFIED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATION_SKIPPED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EVALUATING_COMPENSATION;
import static com.example.payment.domain.enums.PaymentState.LIMITS_EXCEEDED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_FAILED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_FAILED;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_HIT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT_FAILED;
import static com.example.payment.domain.enums.PaymentState.WALLET_SETTLED;
import static com.example.payment.domain.enums.PaymentState.WALLET_SETTLEMENT;

import com.example.payment.domain.enums.PaymentState;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The orthogonal regions of {@code statemachine.puml} and which of their states are terminal.
 *
 * <p>Single source of truth for "which region owns this state" / "has it finished" — shared by the
 * join interceptor, the history interceptor, and both recovery reconcilers so they cannot drift
 * apart. {@code SagaRegionPumlConsistencyTest} fails the build if this enum and the diagram disagree.
 */
@RequiredArgsConstructor
public enum SagaRegion {
    AUTHORIZATION(
            "Authorization",
            PROCESSING,
            EnumSet.of(AUTH_PENDING),
            EnumSet.of(AUTH_APPROVED),
            EnumSet.of(AUTH_REJECTED, AUTH_FAILED)),

    FRAUD_CHECK(
            "FraudCheck",
            PROCESSING,
            EnumSet.of(FRAUD_EVALUATING),
            EnumSet.of(FRAUD_PASSED),
            EnumSet.of(FRAUD_DETECTED, FRAUD_FAILED)),

    // LIMITS_FAILED is a non-failure terminal here
    LIMITS_CHECK(
            "LimitsCheck",
            PROCESSING,
            EnumSet.of(LIMITS_EVALUATING),
            EnumSet.of(LIMITS_OK, LIMITS_FAILED),
            EnumSet.of(LIMITS_EXCEEDED)),

    SANCTIONS_CHECK(
            "SanctionsCheck",
            PROCESSING,
            EnumSet.of(SANCTIONS_EVALUATING),
            EnumSet.of(SANCTIONS_CLEARED, SANCTIONS_FAILED),
            EnumSet.of(SANCTIONS_HIT)),

    FEE_CHECK(
            "FeeCheck",
            PROCESSING,
            EnumSet.of(FEE_EVALUATING, FEE_CALCULATED),
            EnumSet.of(FEE_CHARGED),
            EnumSet.of(FEE_FAILED)),

    // LEDGER_NOTIFICATION_FAILED is a success terminal: by then the wallet
    // settlement already succeeded, and the outbox retries the notification independently.
    SETTLEMENT_REGION(
            "Settlement",
            SETTLEMENT,
            EnumSet.of(WALLET_SETTLEMENT, WALLET_SETTLED),
            EnumSet.of(LEDGER_NOTIFIED, LEDGER_NOTIFICATION_FAILED),
            EnumSet.of(SETTLEMENT_FAILED)),

    FEE_COMPENSATION(
            "FeeCompensation",
            COMPENSATING,
            EnumSet.of(FEE_EVALUATING_COMPENSATION, FEE_COMPENSATING),
            EnumSet.of(FEE_COMPENSATED, FEE_COMPENSATION_SKIPPED),
            EnumSet.noneOf(PaymentState.class)),

    LIMITS_COMPENSATION(
            "LimitsCompensation",
            COMPENSATING,
            EnumSet.of(LIMITS_EVALUATING_COMPENSATION, LIMITS_COMPENSATING),
            EnumSet.of(LIMITS_COMPENSATED, LIMITS_COMPENSATION_SKIPPED),
            EnumSet.noneOf(PaymentState.class));

    public static final String ROOT_REGION = "ROOT";

    @Getter
    private final String label;

    @Getter
    private final PaymentState composite;

    private final Set<PaymentState> inProgressStates;
    private final Set<PaymentState> successTerminalStates;
    private final Set<PaymentState> failureTerminalStates;

    public Set<PaymentState> allStates() {
        EnumSet<PaymentState> all = EnumSet.copyOf(inProgressStates);
        all.addAll(successTerminalStates);
        all.addAll(failureTerminalStates);
        return all;
    }

    public Set<PaymentState> successTerminalStates() {
        return EnumSet.copyOf(successTerminalStates);
    }

    public Set<PaymentState> failureTerminalStates() {
        return failureTerminalStates.isEmpty()
                ? EnumSet.noneOf(PaymentState.class)
                : EnumSet.copyOf(failureTerminalStates);
    }

    public boolean isTerminal(PaymentState state) {
        return successTerminalStates.contains(state) || failureTerminalStates.contains(state);
    }

    public boolean isFailureTerminal(PaymentState state) {
        return failureTerminalStates.contains(state);
    }

    /** True when {@code activeStates} contains a terminal state belonging to this region. */
    public boolean hasFinishedIn(Collection<PaymentState> activeStates) {
        return activeStates.stream().anyMatch(this::isTerminal);
    }

    /** True when {@code activeStates} contains a <em>failure</em> terminal of this region. */
    public boolean hasFailedIn(Collection<PaymentState> activeStates) {
        return activeStates.stream().anyMatch(this::isFailureTerminal);
    }

    /** The regions that make up the given composite state. */
    public static List<SagaRegion> forComposite(PaymentState composite) {
        return Arrays.stream(values())
                .filter(region -> region.composite == composite)
                .toList();
    }

    /** The region owning the given state, if any. */
    public static Optional<SagaRegion> owning(PaymentState state) {
        return Arrays.stream(values())
                .filter(region -> region.allStates().contains(state))
                .findFirst();
    }

    /**
     * Region label for {@code payment_history}, falling back to {@link #ROOT_REGION} for states that
     * genuinely belong to the root machine (NEW, FX_CONVERSION, COMPLETED, FAILED, ...).
     */
    public static String labelOf(PaymentState state) {
        return owning(state).map(SagaRegion::getLabel).orElse(ROOT_REGION);
    }
}

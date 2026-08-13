package com.example.payment.domain.saga;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_SUCCESS;
import static com.example.payment.domain.enums.PaymentState.COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * The three composite states whose orthogonal regions must all finish before the root machine can
 * leave them. The .puml has no join primitive for this ({@code PROCESSING --> SETTLEMENT : COMPLETE}
 * is a plain event transition), so this is the single decision point both the live interceptor and
 * the recovery reconcilers use to detect readiness.
 */
@RequiredArgsConstructor
public enum SagaJoin {
    PROCESSING_JOIN(PROCESSING, COMPLETE, FAIL),
    SETTLEMENT_JOIN(SETTLEMENT, SETTLEMENT_SUCCESS, SETTLEMENT_FAIL),
    COMPENSATING_JOIN(COMPENSATING, FAIL, FAIL);

    private final PaymentState composite;
    private final PaymentEvent successEvent;
    private final PaymentEvent failureEvent;

    public PaymentState composite() {
        return composite;
    }

    public List<SagaRegion> regions() {
        return SagaRegion.forComposite(composite);
    }

    /** One key per composite, not per outcome — a composite joins exactly once, one outcome. */
    public String joinKey() {
        return "SAGA_" + composite.name() + "_JOIN_TRIGGERED";
    }

    /** The join whose composite is present in {@code activeStates}, if any. */
    public static Optional<SagaJoin> activeIn(Collection<PaymentState> activeStates) {
        if (activeStates == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(join -> activeStates.contains(join.composite))
                .findFirst();
    }

    /** The join belonging to a given composite state, if that state has one. */
    public static Optional<SagaJoin> forComposite(PaymentState composite) {
        return Arrays.stream(values())
                .filter(join -> join.composite == composite)
                .findFirst();
    }

    /** The event to dispatch, or empty if any region is still in progress. Requires {@code composite} to be present. */
    public Optional<PaymentEvent> evaluate(Collection<PaymentState> activeStates) {
        if (activeStates == null || !activeStates.contains(composite)) {
            return Optional.empty();
        }
        return evaluateRegions(activeStates);
    }

    /**
     * As {@link #evaluate}, but doesn't require {@code composite} itself to be present — for
     * {@code payment_history}, which accumulates every composite a payment ever visited, so the
     * caller must already know which one it's asking about.
     */
    public Optional<PaymentEvent> evaluateRegions(Collection<PaymentState> regionStates) {
        if (regionStates == null) {
            return Optional.empty();
        }
        List<SagaRegion> regions = regions();
        if (regions.isEmpty()) {
            return Optional.empty();
        }
        boolean allFinished = regions.stream().allMatch(region -> region.hasFinishedIn(regionStates));
        if (!allFinished) {
            return Optional.empty();
        }
        boolean anyFailed = regions.stream().anyMatch(region -> region.hasFailedIn(regionStates));
        return Optional.of(anyFailed ? failureEvent : successEvent);
    }

    /** {@link #activeIn} + {@link #evaluate} for a live machine's state set. */
    public static Optional<JoinDecision> decide(Collection<PaymentState> activeStates) {
        Optional<SagaJoin> joinForState = activeIn(activeStates);

        if (joinForState.isPresent()) {
            return joinForState.get().evaluate(activeStates).map(event -> new JoinDecision(joinForState.get(), event));
        }
        return Optional.empty();
    }

    /** A ready join and the event that should be dispatched for it. */
    public record JoinDecision(SagaJoin join, PaymentEvent event) {}
}

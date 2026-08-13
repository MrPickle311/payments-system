package com.example.payment.application.service;

import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import com.example.payment.domain.saga.SagaJoin.JoinDecision;
import com.example.payment.domain.saga.SagaPendingEvent;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import com.example.payment.infrastructure.persistence.PaymentJpaEntity;
import com.example.payment.infrastructure.persistence.SpringDataPaymentHistoryRepository;
import com.example.payment.infrastructure.persistence.SpringDataPaymentRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovers parallel-saga joins that were decided but never delivered — e.g. the pod died between
 * detection and delivery.
 *
 * <p>{@code @Scheduled} runs on every pod. Layer 1 takes a per-row {@link
 * SagaPendingEventRepository#tryLease lease} so only one pod re-drives a given row per sweep; layers
 * 2/3 route through {@link SagaPendingEventRepository#claim claim()} — the same unique-constraint
 * guard the live dispatch path uses — before re-driving.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaJoinRecoveryScheduler {

    private static final int PENDING_GRACE_SECONDS = 60;

    private static final int STALE_STATE_MINUTES = 2;
    private static final int BATCH_SIZE = 50;

    private static final Duration LEASE_DURATION = Duration.ofSeconds(90);

    private static final String INSTANCE_ID = resolveInstanceId();

    private final SagaPendingEventRepository pendingEventRepository;
    private final SpringDataPaymentRepository paymentRepository;
    private final SpringDataPaymentHistoryRepository paymentHistoryRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    /** Layer 1: claimed but unconfirmed dispatches. */
    @Scheduled(fixedDelay = 30_000)
    public void recoverPendingDispatches() {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minusSeconds(PENDING_GRACE_SECONDS);
        List<SagaPendingEvent> stale = pendingEventRepository.findStalePending(threshold, BATCH_SIZE);
        if (stale.isEmpty()) {
            return;
        }

        log.warn("[JoinRecovery] {} join dispatch(es) claimed but never confirmed", stale.size());
        for (SagaPendingEvent pending : stale) {
            if (!pendingEventRepository.tryLease(pending.id(), INSTANCE_ID, LEASE_DURATION)) {
                continue;
            }
            try {
                if (paymentService.redriveJoin(pending.paymentId(), pending.event())) {
                    pendingEventRepository.markDispatched(pending.paymentId(), pending.joinKey());
                }
            } catch (RuntimeException e) {
                log.error("[JoinRecovery] Re-drive failed for payment {}", pending.paymentId(), e);
                pendingEventRepository.markFailed(pending.paymentId(), pending.joinKey(), String.valueOf(e));
            }
        }
    }

    /** Layers 2 and 3: payments that look joinable but have no live claim. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional(readOnly = true)
    public void reconcileJoinablePayments() {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minusMinutes(STALE_STATE_MINUTES);
        List<PaymentJpaEntity> candidates = paymentRepository.findStuckPayments(threshold, BATCH_SIZE);

        for (PaymentJpaEntity candidate : candidates) {
            resolveJoinFor(candidate).ifPresent(decision -> claimAndRedrive(candidate.getId(), decision));
        }
    }

    private Optional<JoinDecision> resolveJoinFor(PaymentJpaEntity payment) {
        // Layer 2 - the composite itself already proves the join.
        Set<PaymentState> persistedStates = parseStates(payment.getState());
        if (persistedStates.isEmpty()) {
            return Optional.empty();
        }

        Optional<JoinDecision> fromState = SagaJoin.decide(persistedStates);
        if (fromState.isPresent()) {
            log.warn("[JoinRecovery] Payment {} is joinable by persisted state", payment.getId());
            return fromState;
        }

        return getJoinDecisionFromPaymentHistory(payment);
    }

    private Optional<JoinDecision> getJoinDecisionFromPaymentHistory(PaymentJpaEntity payment) {
        Optional<SagaJoin> join = rootStateOf(payment.getState()).flatMap(SagaJoin::forComposite);
        if (join.isEmpty()) {
            return Optional.empty();
        }

        Set<PaymentState> historyStates =
                parseStates(paymentHistoryRepository.findDistinctToStatesByPaymentId(payment.getId()));
        Optional<JoinDecision> fromHistory =
                join.get().evaluateRegions(historyStates).map(event -> new JoinDecision(join.get(), event));
        if (fromHistory.isPresent()) {
            log.warn(
                    "[JoinRecovery] Payment {} is joinable by history but its persisted state ({}) is "
                            + "stale - the claim transaction never committed",
                    payment.getId(),
                    payment.getState());
        }
        return fromHistory;
    }

    /** Loses the claim race silently when another pod (or the live path) already owns this join. */
    private void claimAndRedrive(Long paymentId, JoinDecision decision) {
        String joinKey = decision.join().joinKey();
        if (!pendingEventRepository.claim(paymentId, decision.event(), joinKey)) {
            return;
        }
        try {
            if (paymentService.redriveJoin(paymentId, decision.event())) {
                pendingEventRepository.markDispatched(paymentId, joinKey);
            } else {
                pendingEventRepository.markFailed(
                        paymentId, joinKey, "State machine denied re-driven event " + decision.event());
            }
        } catch (RuntimeException e) {
            log.error("[JoinRecovery] Re-drive failed for payment {}", paymentId, e);
            pendingEventRepository.markFailed(paymentId, joinKey, String.valueOf(e));
        }
    }

    private static String resolveInstanceId() {
        String podName = System.getenv("HOSTNAME");
        if (podName != null && !podName.isBlank()) {
            return podName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException unresolvable) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * The root state of a persisted composite — {@code PaymentStateMachinePersister} always writes it
     * as the first token, with the active region states appended after it.
     */
    private static Optional<PaymentState> rootStateOf(String compositeState) {
        if (compositeState == null || compositeState.isBlank()) {
            return Optional.empty();
        }
        return toState(compositeState.split(",")[0]);
    }

    /** Parses the comma-joined composite written by {@code PaymentStateMachinePersister}. */
    private static Set<PaymentState> parseStates(String compositeState) {
        if (compositeState == null || compositeState.isBlank()) {
            return EnumSet.noneOf(PaymentState.class);
        }
        return parseStates(Arrays.asList(compositeState.split(",")));
    }

    private static Set<PaymentState> parseStates(List<String> stateNames) {
        Set<PaymentState> states = EnumSet.noneOf(PaymentState.class);
        stateNames.stream().map(SagaJoinRecoveryScheduler::toState).forEach(state -> state.ifPresent(states::add));
        return states;
    }

    private static Optional<PaymentState> toState(String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PaymentState.valueOf(trimmed));
        } catch (IllegalArgumentException unknownState) {
            log.warn("[JoinRecovery] Ignoring unrecognised persisted state '{}'", trimmed);
            return Optional.empty();
        }
    }
}

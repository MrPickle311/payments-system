package com.example.payment.infrastructure.statemachine;

import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.application.saga.SagaJoinDispatcher;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;

/**
 * Claims a join durably, then delivers its event asynchronously.
 *
 * <p>The thread hop is load-bearing: {@code postStateChange} runs on a non-blocking Reactor
 * {@code parallel} thread, and both the JDBC claim and {@code sendEvent} block internally. Dispatch
 * inline and the framework swallows the resulting {@code IllegalStateException} with a
 * {@code log.warn} — the join is lost silently. See {@code NonBlockingJoinDispatchRegressionTest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentSagaJoinDispatcher implements SagaJoinDispatcher {

    private final SagaJoinClaimService claimService;
    private final SagaPendingEventRepository pendingEventRepository;
    private final ExecutorService virtualThreadExecutor;

    @Override
    public void dispatch(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine,
            Long paymentId,
            SagaJoin join,
            PaymentEvent event) {

        if (paymentId == null) {
            log.error(
                    "[JoinDispatcher] Cannot dispatch {} for join {} - no PAYMENT_ID in extended "
                            + "state; this payment cannot be recovered automatically",
                    event,
                    join);
            return;
        }

        CompletableFuture.runAsync(
                        () -> claimAndDeliver(rootStateMachine, paymentId, join, event), virtualThreadExecutor)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        log.error(
                                "[JoinDispatcher] Dispatch of {} failed for payment {} (join {})",
                                event,
                                paymentId,
                                join,
                                error);
                        recordFailure(paymentId, join, error);
                    }
                });
    }

    private void claimAndDeliver(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine,
            Long paymentId,
            SagaJoin join,
            PaymentEvent event) {

        String joinKey = join.joinKey();
        boolean owned;
        try {
            owned = claimService.claim(rootStateMachine, paymentId, event, joinKey);
        } catch (DataIntegrityViolationException _) {
            log.debug("""
                [JoinDispatcher] Lost claim race for join {} on payment {}. Another attempt or another pod
                inserted the same (payment_id, join_key) first. Nothing has been written.
                """, joinKey, paymentId);
            return;
        }

        if (!owned) {
            return;
        }

        boolean accepted = SagaContextProxy.sendEventAndReportAcceptance(rootStateMachine, event, paymentId);
        if (accepted) {
            pendingEventRepository.markDispatched(paymentId, joinKey);
        } else {
            // Leave the row PENDING so the recovery pollers retry it against a freshly restored
            // machine - a denial here usually means this machine's in-memory state has drifted.
            pendingEventRepository.markFailed(paymentId, joinKey, "State machine denied event " + event);
        }
    }

    private void recordFailure(Long paymentId, SagaJoin join, Throwable error) {
        try {
            pendingEventRepository.markFailed(paymentId, join.joinKey(), String.valueOf(error));
        } catch (RuntimeException e) {
            log.error("[JoinDispatcher] Could not record dispatch failure for payment {}", paymentId, e);
        }
    }
}

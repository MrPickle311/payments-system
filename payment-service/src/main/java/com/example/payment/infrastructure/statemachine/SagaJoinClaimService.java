package com.example.payment.infrastructure.statemachine;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import com.example.payment.infrastructure.config.PaymentStateMachinePersister;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically claims a join and persists the composite state that justifies it.
 *
 * <p>Both writes must land together. If only the claim committed, a reconciler reading
 * {@code payments.state} would see a payment that does not look joinable and skip it; if only the
 * state committed, two processes could both dispatch. Putting them in one transaction makes
 * "this composite is joinable" and "someone owns delivering its join event" a single durable fact.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaJoinClaimService {

    private final PaymentRepository paymentRepository;
    private final SagaPendingEventRepository pendingEventRepository;
    private final PaymentStateMachinePersister persister;

    /**
     * @return {@code true} if this caller now owns delivering the join event.
     * @throws org.springframework.dao.DataIntegrityViolationException if another process claimed the
     *     same join concurrently — the transaction is rolled back, losing nothing, since the only
     *     other write is a state persist the winner performs identically.
     */
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50))
    public boolean claim(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine,
            Long paymentId,
            PaymentEvent event,
            String joinKey) {

        Optional<Payment> found = paymentRepository.findById(paymentId);
        if (found.isEmpty()) {
            log.warn("[SagaJoinClaim] No payment {} to claim join {} for", paymentId, joinKey);
            return false;
        }

        if (!pendingEventRepository.claim(paymentId, event, joinKey)) {
            log.debug("[SagaJoinClaim] Join {} already claimed for payment {}", joinKey, paymentId);
            return false;
        }

        Payment payment = found.get();
        persister.persist(rootStateMachine, payment);
        paymentRepository.save(payment);
        log.info("[SagaJoinClaim] Claimed join {} for payment {} with event {}", joinKey, paymentId, event);
        return true;
    }
}

package com.example.payments.service;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.entity.Payment;
import com.example.payments.entity.PaymentHistory;
import com.example.payments.enums.PaymentEvent;
import com.example.payments.enums.PaymentState;
import com.example.payments.exception.InvalidTransitionException;
import com.example.payments.exception.PaymentNotFoundException;
import com.example.payments.interceptor.PaymentStateMachineInterceptor;
import com.example.payments.persistence.PaymentStateMachinePersister;
import com.example.payments.repository.PaymentHistoryRepository;
import com.example.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates payment lifecycle operations.
 *
 * <h2>Race-condition prevention strategy</h2>
 * Every state-mutating operation calls {@link PaymentRepository#findByIdWithLock(Long)},
 * which issues a {@code SELECT ... FOR UPDATE} before any state machine work begins.
 * This ensures that if two concurrent webhook deliveries arrive for the same payment
 * (a common scenario with at-least-once delivery guarantees), the second request
 * will block on the row lock until the first transaction commits, then observe the
 * already-updated state and either proceed legally or be rejected.
 *
 * <h2>State machine lifecycle per request</h2>
 * <ol>
 *   <li>A <em>fresh</em> machine is obtained from the factory to avoid state leakage
 *       across requests.</li>
 *   <li>The audit {@link PaymentStateMachineInterceptor} is registered on the instance.</li>
 *   <li>The persister restores the machine to the state stored in the DB.</li>
 *   <li>The event is sent; the result is inspected to detect illegal transitions.</li>
 *   <li>The persister writes the new state back to the entity.</li>
 *   <li>The entity is saved; the machine is stopped to free resources.</li>
 * </ol>
 * All steps run inside a single {@code @Transactional} boundary — the audit history
 * record and the state update are committed atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;
    private final PaymentStateMachineInterceptor stateMachineInterceptor;
    private final PaymentStateMachinePersister stateMachinePersister;

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Creates a new payment in the {@link PaymentState#NEW} state and records
     * the initial history entry so the audit trail starts from the beginning.
     */
    @Transactional
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = Payment.builder()
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .state(PaymentState.NEW.name())
                .build();

        payment = paymentRepository.save(payment);

        // Seed the audit trail with an explicit "born in NEW" record.
        paymentHistoryRepository.save(PaymentHistory.builder()
                .paymentId(payment.getId())
                .fromState("—")
                .toState(PaymentState.NEW.name())
                .event("CREATED")
                .build());

        log.info("[Service] Created payment id={} transactionId={}",
                payment.getId(), payment.getTransactionId());
        return payment;
    }

    /**
     * Processes an event against the payment identified by {@code paymentId}.
     *
     * <p>The row is locked for the duration of the transaction to prevent
     * concurrent modifications.  An {@link InvalidTransitionException} is
     * thrown if the event is not legal for the payment's current state.
     *
     * @param paymentId internal DB identifier
     * @param event     the event to apply
     * @return the updated payment entity (state already mutated and saved)
     * @throws PaymentNotFoundException  if no payment exists for {@code paymentId}
     * @throws InvalidTransitionException if the event is illegal in the current state
     */
    // noRollbackFor is critical for the fraud auto-fail path:
    // when the guard blocks AUTHORIZE, we send FAIL (writing state=FAILED to the DB)
    // and then throw InvalidTransitionException to return HTTP 422 to the caller.
    // Without this, Spring would roll back the FAILED state write on exception.
    @Transactional(noRollbackFor = InvalidTransitionException.class)
    public Payment processEvent(Long paymentId, PaymentEvent event) {

        // ── 1. Lock the row ──────────────────────────────────────────────────
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        log.info("[Service] Processing event={} for payment={} (currentState={})",
                event, paymentId, payment.getState());

        // ── 2. Build a fresh state machine instance ──────────────────────────
        // Using a random UUID as the machine ID avoids any caching / state leakage
        // from previous requests.  State is always authoritative in the DB.
        StateMachine<PaymentState, PaymentEvent> sm =
                stateMachineFactory.getStateMachine(UUID.randomUUID().toString());

        try {
            // ── 3. Register the audit interceptor ────────────────────────────
            sm.getStateMachineAccessor()
                    .doWithAllRegions(access ->
                            access.addStateMachineInterceptor(stateMachineInterceptor));

            // ── 4. Restore machine state from the DB ─────────────────────────
            stateMachinePersister.restore(sm, payment);

            // ── 5. Send the event (reactive API, blocked for imperative flow) ─
            List<StateMachineEventResult<PaymentState, PaymentEvent>> results =
                    sm.sendEvent(Mono.just(
                            MessageBuilder.withPayload(event).build()
                    )).collectList().block();

            // ── 6. Validate the result ────────────────────────────────────────
            //
            // IMPORTANT: Spring State Machine returns ResultType.ACCEPTED when an event
            // *matches a defined transition*, even if a Guard later blocks the state change.
            // ResultType.DENIED is only returned when NO transition exists for the event
            // in the current state (a completely illegal event).
            //
            // We therefore need TWO separate checks:
            //   A) DENIED  → event has no matching transition at all → IllegalTransition
            //   B) ACCEPTED but state unchanged (and not a self-transition)
            //              → guard blocked the transition → handle per-event

            PaymentState stateBefore = payment.currentState();
            PaymentState stateAfter  = sm.getState().getId();

            // (A) No matching transition defined
            boolean denied = results == null || results.isEmpty()
                    || results.stream().allMatch(
                            r -> r.getResultType() == StateMachineEventResult.ResultType.DENIED);
            if (denied) {
                throw new InvalidTransitionException(
                        String.format("Event [%s] is not a legal transition from state [%s] "
                                + "for payment [%d].",
                                event, payment.getState(), paymentId));
            }

            // (B) Event was recognised but a guard blocked the actual state change.
            //     REDIRECT is the only self-transition (PENDING→PENDING) so it's excluded.
            boolean isSelfTransition = (event == PaymentEvent.REDIRECT);
            boolean guardBlocked     = !isSelfTransition && (stateAfter == stateBefore);

            if (guardBlocked) {
                if (event == PaymentEvent.AUTHORIZE) {
                    // Fraud guard blocked AUTHORIZE. Auto-transition to FAILED so
                    // the payment is never left stuck in PENDING.
                    Integer fraudScore = sm.getExtendedState().get("fraudScore", Integer.class);
                    String  fraudRisk  = sm.getExtendedState().get("fraudRisk",  String.class);
                    log.warn("[Service] AUTHORIZE blocked by fraud guard (score={} risk={}) "
                            + "for payment={}. Auto-failing.", fraudScore, fraudRisk, paymentId);

                    sm.sendEvent(Mono.just(
                            MessageBuilder.withPayload(PaymentEvent.FAIL).build()
                    )).collectList().block();
                    stateMachinePersister.persist(sm, payment);
                    paymentRepository.save(payment);

                    throw new InvalidTransitionException(
                            String.format("Payment [%d] rejected by fraud check "
                                    + "(score=%d, risk=%s). Payment moved to FAILED.",
                                    paymentId, fraudScore, fraudRisk));
                }
                // Other guard blocks (e.g. refund window)
                throw new InvalidTransitionException(
                        String.format("Transition [%s] was blocked by a guard for payment [%d] "
                                + "in state [%s].",
                                event, paymentId, payment.getState()));
            }

            // ── 7. Persist new state back to entity ──────────────────────────
            stateMachinePersister.persist(sm, payment);

        } finally {
            // Always stop the machine to release any internal resources / timers.
            sm.stop();
        }

        // ── 8. Save the updated entity (same transaction) ────────────────────
        Payment saved = paymentRepository.save(payment);
        log.info("[Service] Payment {} transitioned to state {}", paymentId, saved.getState());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentHistory> getPaymentHistory(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new PaymentNotFoundException(paymentId);
        }
        return paymentHistoryRepository.findByPaymentIdOrderByTimestampAsc(paymentId);
    }
}

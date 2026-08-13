package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import org.springframework.statemachine.StateMachine;

/**
 * Delivers the event that closes a parallel-saga join. Split out of
 * {@link ParallelSagaJoinInterceptor} so detection stays a pure, database-free function.
 */
public interface SagaJoinDispatcher {

    /**
     * Claim and deliver {@code event} for {@code join}.
     *
     * <p>Called from {@code postStateChange} on a Reactor {@code parallel} thread (non-blocking).
     * Implementations must not block this thread — both the DB work and {@code sendEvent} (which
     * blocks internally) have to move to a thread that permits blocking.
     */
    void dispatch(
            StateMachine<PaymentState, PaymentEvent> rootStateMachine,
            Long paymentId,
            SagaJoin join,
            PaymentEvent event);
}

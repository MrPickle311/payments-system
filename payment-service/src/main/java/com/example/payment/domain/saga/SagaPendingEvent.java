package com.example.payment.domain.saga;

import com.example.payment.domain.enums.PaymentEvent;
import java.time.OffsetDateTime;

/**
 * A claimed-but-not-yet-confirmed parallel-saga join dispatch.
 *
 * <p>Written in the same transaction as the composite state that proves the join is ready, so that
 * a crash after the commit still leaves a durable record of work owed.
 */
public record SagaPendingEvent(
        Long id,
        Long paymentId,
        PaymentEvent event,
        String joinKey,
        Status status,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public enum Status {
        /** Claimed, not yet confirmed delivered. Eligible for re-dispatch once it ages past the grace period. */
        PENDING,
        /** The state machine accepted the event. Terminal. */
        DISPATCHED,
        /** Dispatch was rejected or errored repeatedly; needs operator attention. Terminal. */
        FAILED
    }
}

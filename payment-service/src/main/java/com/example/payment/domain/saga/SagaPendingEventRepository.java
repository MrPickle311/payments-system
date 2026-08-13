package com.example.payment.domain.saga;

import com.example.payment.domain.enums.PaymentEvent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public interface SagaPendingEventRepository {

    /**
     * Claim a join by inserting a {@code PENDING} row.
     *
     * @return {@code true} if this caller now owns the dispatch, {@code false} if the row already
     *     existed (another attempt or pod owns it). The {@code UNIQUE (payment_id, join_key)}
     *     constraint is what makes this durable and cross-process.
     */
    boolean claim(Long paymentId, PaymentEvent event, String joinKey);

    void markDispatched(Long paymentId, String joinKey);

    void markFailed(Long paymentId, String joinKey, String error);

    /**
     * Candidates for recovery — {@code PENDING} rows older than {@code threshold}, excluding rows
     * another pod currently leases. Not a guarantee: {@link #tryLease} must still win the row.
     */
    List<SagaPendingEvent> findStalePending(OffsetDateTime threshold, int batchSize);

    /**
     * Take a time-boxed lease on a row so only one of several polling pods re-drives it per sweep.
     *
     * @return {@code true} if this caller now holds the lease.
     */
    boolean tryLease(Long id, String instanceId, Duration leaseDuration);
}

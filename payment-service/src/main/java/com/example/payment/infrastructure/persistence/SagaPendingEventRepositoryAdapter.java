package com.example.payment.infrastructure.persistence;

import static com.example.payment.domain.saga.SagaPendingEvent.Status.*;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.saga.SagaPendingEvent;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaPendingEventRepositoryAdapter implements SagaPendingEventRepository {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final SpringDataSagaPendingEventRepository pendingEventRepository;
    private final Clock clock;

    /**
     * {@inheritDoc}
     *
     * <p>The pre-check is only a fast path — the real guarantee is {@code UNIQUE (payment_id,
     * join_key)}; a caller that races past the pre-check still loses on the insert.
     */
    @Override
    public boolean claim(Long paymentId, PaymentEvent event, String joinKey) {
        if (pendingEventRepository.findByPaymentIdAndJoinKey(paymentId, joinKey).isPresent()) {
            return false;
        }
        pendingEventRepository.save(SagaPendingEventJpaEntity.builder()
                .paymentId(paymentId)
                .event(event.name())
                .joinKey(joinKey)
                .status(PENDING.name())
                .attempts(1)
                .build());
        return true;
    }

    @Override
    public void markDispatched(Long paymentId, String joinKey) {
        pendingEventRepository.findByPaymentIdAndJoinKey(paymentId, joinKey).ifPresent(entity -> {
            entity.setStatus(DISPATCHED.name());
            entity.setLastError(null);
            releaseLease(entity);
            pendingEventRepository.save(entity);
        });
    }

    @Override
    public void markFailed(Long paymentId, String joinKey, String error) {
        pendingEventRepository.findByPaymentIdAndJoinKey(paymentId, joinKey).ifPresent(entity -> {
            entity.setStatus(PENDING.name());
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setLastError(truncate(error));
            releaseLease(entity);
            pendingEventRepository.save(entity);
        });
    }

    @Override
    public List<SagaPendingEvent> findStalePending(OffsetDateTime threshold, int batchSize) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return pendingEventRepository.findStalePending(threshold, now, batchSize).stream()
                .map(SagaPendingEventRepositoryAdapter::toDomain)
                .toList();
    }

    /** Needed because {@code @Modifying} queries require an active transaction; the caller has none. */
    @Override
    @Transactional
    public boolean tryLease(Long id, String instanceId, Duration leaseDuration) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = pendingEventRepository.tryLease(id, instanceId, now.plus(leaseDuration), now);
        return updated == 1;
    }

    private static void releaseLease(SagaPendingEventJpaEntity entity) {
        entity.setLockedBy(null);
        entity.setLockedUntil(null);
    }

    private static SagaPendingEvent toDomain(SagaPendingEventJpaEntity entity) {
        return new SagaPendingEvent(
                entity.getId(),
                entity.getPaymentId(),
                PaymentEvent.valueOf(entity.getEvent()),
                entity.getJoinKey(),
                valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}

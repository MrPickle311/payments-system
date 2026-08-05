package com.example.payments.common.sharedkernel.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRepositoryProxy {

    private final OutboxRepository outboxRepository;

    @Retryable(
            retryFor = {DataAccessException.class, Exception.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 2000))
    public OutboxEventEntity save(OutboxEventEntity entity) {
        log.debug(
                "[OutboxProxy] Saving outbox event aggregateId={} eventType={}",
                entity.getAggregateId(),
                entity.getEventType());
        return outboxRepository.save(entity);
    }
}

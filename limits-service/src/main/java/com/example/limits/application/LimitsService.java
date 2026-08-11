package com.example.limits.application;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LimitsService {

    private final LimitsTransactionalOperations transactionalOperations;

    @Retryable(
            retryFor = {DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 50))
    public boolean checkAndConsume(Long userId, BigDecimal amount, String idempotencyKey) {
        return transactionalOperations.checkAndConsume(userId, amount, idempotencyKey);
    }

    @Retryable(
            retryFor = {DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 50))
    public void release(Long userId, BigDecimal amount, String idempotencyKey) {
        transactionalOperations.release(userId, amount, idempotencyKey);
    }
}

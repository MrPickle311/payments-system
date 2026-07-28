package com.example.limits.application;

import com.example.limits.domain.DailyLimit;
import com.example.limits.domain.DailyLimitRepository;
import com.example.limits.infrastructure.persistence.IdempotencyKeyEntity;
import com.example.limits.infrastructure.persistence.IdempotencyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitsService {

    private final DailyLimitRepository dailyLimitRepository;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    @Retryable(
            retryFor = {DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 50))
    public boolean checkAndConsume(Long userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                log.info("[LimitsService] Duplicate checkAndConsume, returning cached status for key={}", idempotencyKey);
                return "true".equals(existing.get().getStatus());
            }
        }

        LocalDate today = getNow();
        DailyLimit limit = dailyLimitRepository
                .findByUserIdAndDate(userId, today)
                .orElseGet(() -> DailyLimit.fresh(userId, today));
        if (!limit.allows(amount)) {
            return handleLimitExceeded(userId, limit.getAmountUsed(), amount, idempotencyKey);
        }
        limit.consume(amount);
        dailyLimitRepository.save(limit);
        log.info("[LimitsService] Limit consumed userId={} totalUsedToday={}", userId, limit.getAmountUsed());
        
        return saveIdempotencyAndReturn(idempotencyKey, true);
    }

    private boolean handleLimitExceeded(Long userId, BigDecimal amountUsed, BigDecimal requested, String idempotencyKey) {
        log.warn(
                "[LimitsService] Daily limit exceeded userId={} amountUsed={} requested={}",
                userId,
                amountUsed,
                requested);
        return saveIdempotencyAndReturn(idempotencyKey, false);
    }

    @Transactional
    public void release(Long userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                log.info("[LimitsService] Duplicate release, ignoring for key={}", idempotencyKey);
                return;
            }
        }

        LocalDate today = getNow();
        dailyLimitRepository.findByUserIdAndDate(userId, today).ifPresent(limit -> {
            limit.release(amount);
            dailyLimitRepository.save(limit);
            log.info("[LimitsService] Limit released userId={} newUsed={}", userId, limit.getAmountUsed());
        });
        saveIdempotencyAndReturn(idempotencyKey, true);
    }

    private boolean saveIdempotencyAndReturn(String key, boolean status) {
        if (key != null && !key.isBlank()) {
            idempotencyRepository.save(IdempotencyKeyEntity.builder()
                    .idempotencyKey(key)
                    .status(String.valueOf(status))
                    .build());
        }
        return status;
    }

    private static LocalDate getNow() {
        return LocalDate.now(ZoneId.systemDefault());
    }
}

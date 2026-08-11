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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class LimitsTransactionalOperations {

    private final DailyLimitRepository dailyLimitRepository;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    boolean checkAndConsume(Long userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            int claimed = idempotencyRepository.tryInsert(idempotencyKey);
            if (claimed == 0) {
                var existing = idempotencyRepository
                        .findById(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "idempotency row missing after conflict for key=" + idempotencyKey));
                log.info(
                        "[LimitsService] Duplicate checkAndConsume, returning cached status for key={}",
                        idempotencyKey);
                return "true".equals(existing.getStatus());
            }
        }

        LocalDate today = getNow();
        DailyLimit limit = dailyLimitRepository
                .findByUserIdAndDate(userId, today)
                .orElseGet(() -> DailyLimit.fresh(userId, today));
        if (!limit.allows(amount)) {
            handleLimitExceeded(userId, limit.getAmountUsed(), amount, idempotencyKey);
            return false;
        }
        limit.consume(amount);
        dailyLimitRepository.save(limit);

        log.info("[LimitsService] Limit consumed userId={} totalUsedToday={}", userId, limit.getAmountUsed());

        saveIdempotencyAndReturn(idempotencyKey, true);
        return true;
    }

    @Transactional
    void release(Long userId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            int claimed = idempotencyRepository.tryInsert(idempotencyKey);
            if (claimed == 0) {
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

    private void handleLimitExceeded(
            Long userId, BigDecimal amountUsed, BigDecimal requested, String idempotencyKey) {
        log.warn(
                "[LimitsService] Daily limit exceeded userId={} amountUsed={} requested={}",
                userId,
                amountUsed,
                requested);
        saveIdempotencyAndReturn(idempotencyKey, false);
    }

    private void saveIdempotencyAndReturn(String key, boolean status) {
        if (key != null && !key.isBlank()) {
            idempotencyRepository.save(IdempotencyKeyEntity.builder()
                    .idempotencyKey(key)
                    .status(String.valueOf(status))
                    .build());
        }
    }

    private static LocalDate getNow() {
        return LocalDate.now(ZoneId.systemDefault());
    }
}

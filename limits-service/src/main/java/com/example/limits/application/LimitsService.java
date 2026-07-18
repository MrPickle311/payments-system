package com.example.limits.application;

import com.example.limits.domain.DailyLimit;
import com.example.limits.domain.DailyLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitsService {

  private final DailyLimitRepository dailyLimitRepository;

  @Transactional
  public boolean checkAndConsume(Long userId, BigDecimal amount) {
    LocalDate today = getNow();
    DailyLimit limit = dailyLimitRepository.findByUserIdAndDate(userId, today)
        .orElseGet(() -> DailyLimit.fresh(userId, today));
    if (!limit.allows(amount)) {
      return handleLimitExceeded(userId, limit.getAmountUsed(), amount);
    }
    limit.consume(amount);
    dailyLimitRepository.save(limit);
    log.info("[LimitsService] Limit consumed userId={} totalUsedToday={}", userId,
        limit.getAmountUsed());
    return true;
  }

  private boolean handleLimitExceeded(Long userId, BigDecimal amountUsed, BigDecimal requested) {
    log.warn("[LimitsService] Daily limit exceeded userId={} amountUsed={} requested={}", userId,
        amountUsed, requested);
    return false;
  }

  @Transactional
  public void release(Long userId, BigDecimal amount) {
    LocalDate today = getNow();
    dailyLimitRepository.findByUserIdAndDate(userId, today).ifPresent(limit -> {
      limit.release(amount);
      dailyLimitRepository.save(limit);
      log.info("[LimitsService] Limit released userId={} newUsed={}", userId,
          limit.getAmountUsed());
    });
  }

  private static LocalDate getNow() {
    return LocalDate.now(ZoneId.systemDefault());
  }
}

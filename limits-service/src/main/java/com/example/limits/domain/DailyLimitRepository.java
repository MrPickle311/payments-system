package com.example.limits.domain;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyLimitRepository {
  Optional<DailyLimit> findByUserIdAndDate(Long userId, LocalDate date);

  DailyLimit save(DailyLimit limit);
}

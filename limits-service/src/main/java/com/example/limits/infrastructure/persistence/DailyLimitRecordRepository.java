package com.example.limits.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyLimitRecordRepository extends JpaRepository<DailyLimitRecordEntity, Long> {

  Optional<DailyLimitRecordEntity> findByUserIdAndDate(Long userId, LocalDate date);
}

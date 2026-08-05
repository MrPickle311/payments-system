package com.example.limits.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyLimitRecordRepository extends JpaRepository<DailyLimitRecordEntity, Long> {

    @Query("SELECT d FROM DailyLimitRecordEntity d WHERE d.userId = :userId AND d.date = :date")
    Optional<DailyLimitRecordEntity> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}

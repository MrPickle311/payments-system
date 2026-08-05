package com.example.limits.infrastructure.persistence;

import com.example.limits.domain.DailyLimit;
import com.example.limits.domain.DailyLimitRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyLimitRepositoryAdapter implements DailyLimitRepository {

    private final DailyLimitRecordRepository jpaRepository;

    @Override
    public Optional<DailyLimit> findByUserIdAndDate(Long userId, LocalDate date) {
        return jpaRepository
                .findByUserIdAndDate(userId, date)
                .map(e -> new DailyLimit(e.getUserId(), e.getDate(), e.getAmountUsed()));
    }

    @Override
    public DailyLimit save(DailyLimit limit) {
        DailyLimitRecordEntity entity = jpaRepository
                .findByUserIdAndDate(limit.getUserId(), limit.getDate())
                .orElseGet(() -> DailyLimitRecordEntity.builder()
                        .userId(limit.getUserId())
                        .date(limit.getDate())
                        .amountUsed(limit.getAmountUsed())
                        .build());

        entity.setAmountUsed(limit.getAmountUsed());
        jpaRepository.save(entity);
        return limit;
    }
}

package com.example.limits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DailyLimit {

    private static final BigDecimal DAILY_LIMIT_AMOUNT = new BigDecimal("10000.00");

    private final Long userId;
    private final LocalDate date;
    private BigDecimal amountUsed;

    public static DailyLimit fresh(Long userId, LocalDate date) {
        return new DailyLimit(userId, date, BigDecimal.ZERO);
    }

    public boolean allows(BigDecimal amount) {
        return amountUsed.add(amount).compareTo(DAILY_LIMIT_AMOUNT) <= 0;
    }

    public void consume(BigDecimal amount) {
        amountUsed = amountUsed.add(amount);
    }

    public void release(BigDecimal amount) {
        amountUsed = amountUsed.subtract(amount).max(BigDecimal.ZERO);
    }
}

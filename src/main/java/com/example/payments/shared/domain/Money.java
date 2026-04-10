package com.example.payments.shared.domain;

import lombok.ToString;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object representing an amount of money in a specific currency.
 * Immutable and self-validating.
 */
@ToString
@org.jmolecules.ddd.annotation.ValueObject
public class Money {

    private final BigDecimal amount;
    private final String currency;

    public Money() {
        this.amount = BigDecimal.ZERO;
        this.currency = "USD";
    }

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        return new Money(amount, currency.toUpperCase());
    }

    public static Money of(String amount, String currency) {
        return of(new BigDecimal(amount), currency);
    }
    
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal multiplier, int scale, RoundingMode roundingMode) {
        return new Money(this.amount.multiply(multiplier).setScale(scale, roundingMode), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    String.format("Cannot operate on different currencies: %s and %s", 
                            this.currency, other.currency));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        int result = amount.hashCode();
        result = 31 * result + currency.hashCode();
        return result;
    }
}

package com.example.payments.common.sharedkernel;

import org.jmolecules.ddd.annotation.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;


@ValueObject
public record Money(BigDecimal amount, String currency) {

  public Money() {
    this(BigDecimal.ZERO, "USD");
  }

  public static Money of(final BigDecimal amountValue, final String currencyCode) {
    if (amountValue == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }
    if (currencyCode == null || currencyCode.trim().isEmpty()) {
      throw new IllegalArgumentException("Currency cannot be empty");
    }
    return new Money(amountValue, currencyCode.toUpperCase());
  }

  public static Money of(final String amountValue, final String currencyCode) {
    return of(new BigDecimal(amountValue), currencyCode);
  }

  public Money add(final Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.add(other.amount), this.currency);
  }

  public Money subtract(final Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.subtract(other.amount), this.currency);
  }

  public Money multiply(final BigDecimal multiplier, final int scale,
                        final RoundingMode roundingMode) {
    return new Money(this.amount.multiply(multiplier).setScale(scale, roundingMode), this.currency);
  }

  private void requireSameCurrency(final Money other) {
    if (!this.currency.equals(other.currency)) {
      throw new IllegalArgumentException(String.format(
              "Cannot operate on different currencies: %s and %s", this.currency, other.currency));
    }
  }
}

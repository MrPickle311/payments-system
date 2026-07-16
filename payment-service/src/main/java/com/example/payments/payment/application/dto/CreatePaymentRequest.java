package com.example.payments.payment.application.dto;

import com.example.payments.sharedkernel.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class CreatePaymentRequest {

  @NotBlank(message = "transactionId is required")
  private String transactionId;

  @NotNull(message = "amount is required")
  @Positive(message = "amount must be positive")
  private BigDecimal amount;

  @NotBlank(message = "currency is required")
  @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
  private String currency;

  private String sourceCurrency;

  public CreatePaymentRequest(String transactionId, BigDecimal amount, String currency) {
    this.transactionId = transactionId;
    this.amount = amount;
    this.currency = currency;
    this.sourceCurrency = currency;
  }

    public Money getMoney() {
    return Money.of(amount, currency);
  }
}

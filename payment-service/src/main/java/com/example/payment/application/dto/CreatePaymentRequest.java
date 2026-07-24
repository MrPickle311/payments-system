package com.example.payment.application.dto;

import com.example.payments.common.sharedkernel.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;

    @NotNull(message = "sourceUserId is required")
    private Long sourceUserId;

    @NotNull(message = "targetUserId is required")
    private Long targetUserId;

    @NotBlank(message = "sourceCurrency is required")
    @Size(min = 3, max = 3, message = "sourceCurrency must be a 3-letter ISO 4217 code")
    private String sourceCurrency;

    @NotBlank(message = "targetCurrency is required")
    @Size(min = 3, max = 3, message = "targetCurrency must be a 3-letter ISO 4217 code")
    private String targetCurrency;

    public Money getMoney() {
        return Money.of(amount, currency);
    }
}

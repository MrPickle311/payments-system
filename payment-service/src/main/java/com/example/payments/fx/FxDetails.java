package com.example.payments.fx;

import java.math.BigDecimal;

public record FxDetails(String sourceCurrency, BigDecimal sourceAmount, BigDecimal exchangeRate) {
}

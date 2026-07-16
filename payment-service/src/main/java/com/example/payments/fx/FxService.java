package com.example.payments.fx;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FxService {

    public FxDetails calculateFx(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        if (sourceCurrency == null || sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return new FxDetails(targetCurrency, amount, BigDecimal.ONE);
        }
        BigDecimal midRate = getMidRate(sourceCurrency, targetCurrency);
        BigDecimal rate = midRate.multiply(new BigDecimal("1.01")).setScale(6, RoundingMode.HALF_UP);
        BigDecimal srcAmount = amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        return new FxDetails(sourceCurrency, srcAmount, rate);
    }

    private BigDecimal getMidRate(String source, String target) {
        if ("EUR".equalsIgnoreCase(source) && "USD".equalsIgnoreCase(target)) {
            return new BigDecimal("0.9091");
        }
        if ("USD".equalsIgnoreCase(source) && "EUR".equalsIgnoreCase(target)) {
            return new BigDecimal("1.10");
        }
        if ("PLN".equalsIgnoreCase(source) && "USD".equalsIgnoreCase(target)) {
            return new BigDecimal("0.25");
        }
        if ("USD".equalsIgnoreCase(source) && "PLN".equalsIgnoreCase(target)) {
            return new BigDecimal("4.00");
        }
        return BigDecimal.ONE;
    }
}

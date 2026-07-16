package com.example.payments.payment.api;

import java.math.BigDecimal;

public record RefundRequest(BigDecimal amount, String reason) {
}

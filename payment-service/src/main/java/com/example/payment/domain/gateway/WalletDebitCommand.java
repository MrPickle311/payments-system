package com.example.payment.domain.gateway;

public record WalletDebitCommand(
        Long paymentId,
        Long sourceUserId,
        Long targetUserId,
        String amount,
        String currency,
        String idempotencyKey) {}

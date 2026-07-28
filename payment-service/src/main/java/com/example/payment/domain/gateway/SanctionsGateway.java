package com.example.payment.domain.gateway;

public interface SanctionsGateway {
    boolean checkSanctions(Long paymentId, Long sourceUserId, Long targetUserId);
}

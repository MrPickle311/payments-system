package com.example.payment.domain.gateway;

import com.example.payments.sanctions.grpc.SanctionsResponse;

public interface SanctionsGateway {
  SanctionsResponse checkSanctions(Long paymentId, Long sourceUserId, Long targetUserId);
}

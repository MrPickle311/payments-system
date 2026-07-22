package com.example.payment.domain.gateway;

import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.limits.grpc.ReleaseLimitResponse;

public interface LimitsGateway {
  LimitsResponse checkLimits(Long paymentId, Long sourceUserId, String amount, String currency);

  ReleaseLimitResponse releaseLimit(Long paymentId, Long sourceUserId, String amount);
}

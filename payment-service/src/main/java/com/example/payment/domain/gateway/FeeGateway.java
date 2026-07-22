package com.example.payment.domain.gateway;

import com.example.payments.fee.grpc.FeeResponse;

public interface FeeGateway {
  FeeResponse calculateFee(Long paymentId, String amount, String currency);
}

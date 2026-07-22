package com.example.payment.domain.gateway;

import com.example.payments.authorization.grpc.AuthorizationResponse;

public interface AuthorizationGateway {
  AuthorizationResponse authorize(Long paymentId);
}

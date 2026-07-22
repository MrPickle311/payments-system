package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.AuthorizationGateway;
import com.example.payments.authorization.grpc.AuthorizationRequest;
import com.example.payments.authorization.grpc.AuthorizationResponse;
import com.example.payments.authorization.grpc.AuthorizationServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcAuthorizationGatewayAdapter implements AuthorizationGateway {

  @GrpcClient("authorization-service")
  private final AuthorizationServiceGrpc.AuthorizationServiceBlockingStub authorizationService;

  @Override
  public AuthorizationResponse authorize(Long paymentId) {
    return authorizationService.authorize(
        AuthorizationRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L).build());
  }
}

package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.SanctionsGateway;
import com.example.payments.sanctions.grpc.SanctionsRequest;
import com.example.payments.sanctions.grpc.SanctionsResponse;
import com.example.payments.sanctions.grpc.SanctionsServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcSanctionsGatewayAdapter implements SanctionsGateway {

  @GrpcClient("sanctions-service")
  private final SanctionsServiceGrpc.SanctionsServiceBlockingStub sanctionsService;

  @Override
  public SanctionsResponse checkSanctions(Long paymentId, Long sourceUserId, Long targetUserId) {
    return sanctionsService.checkSanctions(
        SanctionsRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L)
            .setSourceUserId(sourceUserId != null ? sourceUserId : 0L)
            .setTargetUserId(targetUserId != null ? targetUserId : 0L).build());
  }
}

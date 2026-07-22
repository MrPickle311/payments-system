package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.LimitsGateway;
import com.example.payments.limits.grpc.LimitsRequest;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.limits.grpc.LimitsServiceGrpc;
import com.example.payments.limits.grpc.ReleaseLimitRequest;
import com.example.payments.limits.grpc.ReleaseLimitResponse;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcLimitsGatewayAdapter implements LimitsGateway {

  private static final String ZERO_AMOUNT = "0";
  private static final String EMPTY_STRING = "";

  @GrpcClient("limits-service")
  private final LimitsServiceGrpc.LimitsServiceBlockingStub limitsService;

  @Override
  public LimitsResponse checkLimits(Long paymentId, Long sourceUserId, String amount,
      String currency) {
    return limitsService
        .checkLimits(LimitsRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L)
            .setSourceUserId(sourceUserId != null ? sourceUserId : 0L)
            .setAmount(amount != null ? amount : ZERO_AMOUNT)
            .setCurrency(currency != null ? currency : EMPTY_STRING).build());
  }

  @Override
  public ReleaseLimitResponse releaseLimit(Long paymentId, Long sourceUserId, String amount) {
    return limitsService.releaseLimit(
        ReleaseLimitRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L)
            .setSourceUserId(sourceUserId != null ? sourceUserId : 0L)
            .setAmount(amount != null ? amount : ZERO_AMOUNT).build());
  }
}

package com.example.limits.api;

import com.example.limits.application.LimitsService;
import com.example.payments.limits.grpc.LimitsRequest;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.limits.grpc.LimitsServiceGrpc;
import com.example.payments.limits.grpc.ReleaseLimitRequest;
import com.example.payments.limits.grpc.ReleaseLimitResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LimitsGrpcService extends LimitsServiceGrpc.LimitsServiceImplBase {

  private final LimitsService limitsService;

  @Override
  public void checkLimits(LimitsRequest request, StreamObserver<LimitsResponse> responseObserver) {
    long sourceUserId = request.getSourceUserId();
    BigDecimal amount = parseAmount(request.getAmount());
    log.info("[LimitsGrpc] CheckLimits paymentId={} userId={} amount={}", request.getPaymentId(),
        sourceUserId, amount);

    boolean allowed = limitsService.checkAndConsume(sourceUserId, amount);
    responseObserver.onNext(LimitsResponse.newBuilder().setSuccess(allowed).build());
    responseObserver.onCompleted();
  }

  @Override
  public void releaseLimit(ReleaseLimitRequest request,
      StreamObserver<ReleaseLimitResponse> responseObserver) {
    long sourceUserId = request.getSourceUserId();
    BigDecimal amount = parseAmount(request.getAmount());
    log.info("[LimitsGrpc] ReleaseLimit paymentId={} userId={} amount={}", request.getPaymentId(),
        sourceUserId, amount);

    limitsService.release(sourceUserId, amount);
    responseObserver.onNext(ReleaseLimitResponse.newBuilder().setReleased(true).build());
    responseObserver.onCompleted();
  }

  private BigDecimal parseAmount(String raw) {
    try {
      return new BigDecimal(raw);
    } catch (NumberFormatException e) {
      log.error("[LimitsGrpc] Invalid amount '{}', defaulting to 0", raw, e);
      return ZERO;
    }
  }
}

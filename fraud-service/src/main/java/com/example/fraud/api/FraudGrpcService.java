package com.example.fraud.api;

import com.example.fraud.application.FraudCheckPort;
import com.example.fraud.application.FraudResult;
import com.example.payments.common.sharedkernel.Money;
import com.example.payments.fraud.grpc.FraudRequest;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.fraud.grpc.FraudServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FraudGrpcService extends FraudServiceGrpc.FraudServiceImplBase {

  private final FraudCheckPort fraudCheckPort;

  @Override
  public void checkFraud(FraudRequest req, StreamObserver<FraudResponse> observer) {
    long payId = req.getPaymentId();
    log.info("[FraudGrpc] CheckFraud paymentId={}", payId);
    try {
      Money money = Money.of(new BigDecimal(req.getAmount()), req.getCurrency());
      FraudResult res = fraudCheckPort.evaluate(payId, money);
      observer.onNext(buildResponse(res));
    } catch (Exception e) {
      log.error("[FraudGrpc] Error evaluating fraud for paymentId={}", payId, e);
      observer.onNext(FraudResponse.newBuilder().setSuccess(false).build());
    }
    observer.onCompleted();
  }

  private FraudResponse buildResponse(FraudResult res) {
    return FraudResponse.newBuilder().setSuccess(!res.isHighRisk()).setScore(res.score())
        .setRiskLevel(res.riskLevel()).setRecommendation(res.recommendation()).build();
  }
}

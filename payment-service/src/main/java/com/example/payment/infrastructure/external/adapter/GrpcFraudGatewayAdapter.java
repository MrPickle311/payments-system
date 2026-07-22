package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.FraudGateway;
import com.example.payments.fraud.grpc.FraudRequest;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.fraud.grpc.FraudServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcFraudGatewayAdapter implements FraudGateway {

  private static final String ZERO_AMOUNT = "0";
  private static final String EMPTY_STRING = "";

  @GrpcClient("fraud-service")
  private final FraudServiceGrpc.FraudServiceBlockingStub fraudCheckService;

  @Override
  public FraudResponse checkFraud(Long paymentId, String amount, String currency) {
    return fraudCheckService
        .checkFraud(FraudRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L)
            .setAmount(amount != null ? amount : ZERO_AMOUNT)
            .setCurrency(currency != null ? currency : EMPTY_STRING).build());
  }
}

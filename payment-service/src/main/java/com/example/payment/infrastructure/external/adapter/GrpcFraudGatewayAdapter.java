package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.FraudGateway;
import com.example.payments.fraud.grpc.FraudRequest;
import com.example.payments.fraud.grpc.FraudResponse;
import com.example.payments.fraud.grpc.FraudServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@RequiredArgsConstructor
public class GrpcFraudGatewayAdapter implements FraudGateway {

    private static final String ZERO_AMOUNT = "0";
    private static final String EMPTY_STRING = "";

    @GrpcClient("fraud-service")
    private final FraudServiceGrpc.FraudServiceBlockingStub fraudCheckService;

    @Override
    @CircuitBreaker(name = "fraudService")
    @Retry(name = "fraudService")
    public FraudResponse checkFraud(Long paymentId, String amount, String currency) {
        return fraudCheckService
                .withDeadlineAfter(3, SECONDS)
                .checkFraud(FraudRequest.newBuilder()
                        .setPaymentId(paymentId != null ? paymentId : 0L)
                        .setAmount(amount != null ? amount : ZERO_AMOUNT)
                        .setCurrency(currency != null ? currency : EMPTY_STRING)
                        .build());
    }
}

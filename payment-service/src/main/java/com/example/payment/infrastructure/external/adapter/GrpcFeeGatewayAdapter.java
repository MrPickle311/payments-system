package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.FeeGateway;
import com.example.payments.fee.grpc.FeeRequest;
import com.example.payments.fee.grpc.FeeResponse;
import com.example.payments.fee.grpc.FeeServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcFeeGatewayAdapter implements FeeGateway {

    private static final String ZERO_AMOUNT = "0";
    private static final String EMPTY_STRING = "";

    @GrpcClient("fee-service")
    private final FeeServiceGrpc.FeeServiceBlockingStub feeService;

    @Override
    @CircuitBreaker(name = "feeService")
    @Retry(name = "feeService")
    public FeeResponse calculateFee(Long paymentId, String amount, String currency) {
        return feeService
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .calculateFee(FeeRequest.newBuilder()
                        .setPaymentId(paymentId != null ? paymentId : 0L)
                        .setAmount(amount != null ? amount : ZERO_AMOUNT)
                        .setCurrency(currency != null ? currency : EMPTY_STRING)
                        .build());
    }
}

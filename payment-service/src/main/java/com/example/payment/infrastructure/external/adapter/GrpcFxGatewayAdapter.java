package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.FxGateway;
import com.example.payments.fx.grpc.FxRequest;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.fx.grpc.FxServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import static java.util.concurrent.TimeUnit.SECONDS;

@Component
@RequiredArgsConstructor
public class GrpcFxGatewayAdapter implements FxGateway {

    private static final String ZERO_AMOUNT = "0";
    private static final String EMPTY_STRING = "";

    @GrpcClient("fx-service")
    private final FxServiceGrpc.FxServiceBlockingStub fxService;

    @Override
    @CircuitBreaker(name = "fxService")
    @Retry(name = "fxService")
    public FxResponse processFx(Long paymentId, String amount, String sourceCurrency, String targetCurrency) {
        return fxService
                .withDeadlineAfter(3, SECONDS)
                .processFx(FxRequest.newBuilder()
                        .setPaymentId(paymentId != null ? paymentId : 0L)
                        .setAmount(amount != null ? amount : ZERO_AMOUNT)
                        .setSourceCurrency(sourceCurrency != null ? sourceCurrency : EMPTY_STRING)
                        .setTargetCurrency(getTargetCurrency(sourceCurrency, targetCurrency))
                        .build());
    }

    private static String getTargetCurrency(String sourceCurrency, String targetCurrency) {
        if (targetCurrency != null) {
            return targetCurrency;
        }

        return sourceCurrency != null ? sourceCurrency : EMPTY_STRING;
    }
}

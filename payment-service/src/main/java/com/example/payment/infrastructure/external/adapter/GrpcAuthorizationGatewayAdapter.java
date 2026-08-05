package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.AuthorizationGateway;
import com.example.payments.authorization.grpc.AuthorizationRequest;
import com.example.payments.authorization.grpc.AuthorizationResponse;
import com.example.payments.authorization.grpc.AuthorizationServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcAuthorizationGatewayAdapter implements AuthorizationGateway {

    @GrpcClient("authorization-service")
    private final AuthorizationServiceGrpc.AuthorizationServiceBlockingStub authorizationService;

    @Override
    @CircuitBreaker(name = "authorizationService")
    @Retry(name = "authorizationService")
    public AuthorizationResponse authorize(Long paymentId) {
        return authorizationService
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .authorize(AuthorizationRequest.newBuilder()
                        .setPaymentId(paymentId != null ? paymentId : 0L)
                        .build());
    }
}

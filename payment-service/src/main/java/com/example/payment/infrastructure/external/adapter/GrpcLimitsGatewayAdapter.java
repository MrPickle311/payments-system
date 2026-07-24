package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.LimitsGateway;
import com.example.payments.limits.grpc.LimitsRequest;
import com.example.payments.limits.grpc.LimitsResponse;
import com.example.payments.limits.grpc.LimitsServiceGrpc;
import com.example.payments.limits.grpc.ReleaseLimitRequest;
import com.example.payments.limits.grpc.ReleaseLimitResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcLimitsGatewayAdapter implements LimitsGateway {

    @GrpcClient("limits-service")
    private final LimitsServiceGrpc.LimitsServiceBlockingStub limitsService;

    @Override
    @CircuitBreaker(name = "limitsService")
    @Retry(name = "limitsService")
    public LimitsResponse checkLimits(
            Long paymentId, Long sourceUserId, String amount, String currency, String idempotencyKey) {
        log.info(
                "[Adapter] checkLimits over gRPC for paymentId={} userId={} amount={} currency={}",
                paymentId,
                sourceUserId,
                amount,
                currency);
        var request = LimitsRequest.newBuilder()
                .setPaymentId(paymentId)
                .setSourceUserId(sourceUserId)
                .setAmount(amount)
                .setCurrency(currency)
                .setIdempotencyKey(idempotencyKey)
                .build();
        return limitsService.withDeadlineAfter(3, SECONDS).checkLimits(request);
    }

    @Override
    @CircuitBreaker(name = "limitsService")
    @Retry(name = "limitsService")
    public ReleaseLimitResponse releaseLimit(
            Long paymentId, Long sourceUserId, String amount, String idempotencyKey) {
        log.info(
                "[Adapter] releaseLimit over gRPC for paymentId={} userId={} amount={}",
                paymentId,
                sourceUserId,
                amount);
        var request = ReleaseLimitRequest.newBuilder()
                .setPaymentId(paymentId)
                .setSourceUserId(sourceUserId)
                .setAmount(amount)
                .setIdempotencyKey(idempotencyKey)
                .build();
        return limitsService.withDeadlineAfter(3, SECONDS).releaseLimit(request);
    }
}

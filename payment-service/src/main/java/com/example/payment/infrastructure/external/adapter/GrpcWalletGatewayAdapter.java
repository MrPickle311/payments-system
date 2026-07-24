package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.WalletDebitCommand;
import com.example.payment.domain.gateway.WalletGateway;
import com.example.payments.wallet.grpc.DebitRequest;
import com.example.payments.wallet.grpc.DebitResponse;
import com.example.payments.wallet.grpc.WalletServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrpcWalletGatewayAdapter implements WalletGateway {

    private static final String ZERO_AMOUNT = "0";
    private static final String EMPTY_STRING = "";

    @GrpcClient("wallet-service")
    private final WalletServiceGrpc.WalletServiceBlockingStub walletService;

    @Override
    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService")
    public DebitResponse debit(WalletDebitCommand command) {
        return walletService
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .debit(DebitRequest.newBuilder()
                        .setPaymentId(command.getPaymentId() != null ? command.getPaymentId() : 0L)
                        .setSourceUserId(command.getSourceUserId() != null ? command.getSourceUserId() : 0L)
                        .setTargetUserId(command.getTargetUserId() != null ? command.getTargetUserId() : 0L)
                        .setAmount(command.getAmount() != null ? command.getAmount() : ZERO_AMOUNT)
                        .setCurrency(command.getCurrency() != null ? command.getCurrency() : EMPTY_STRING)
                        .setIdempotencyKey(
                                command.getIdempotencyKey() != null ? command.getIdempotencyKey() : EMPTY_STRING)
                        .build());
    }
}

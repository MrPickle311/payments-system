package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.WalletDebitCommand;
import com.example.payment.domain.gateway.WalletGateway;
import com.example.payments.wallet.grpc.DebitRequest;
import com.example.payments.wallet.grpc.DebitResponse;
import com.example.payments.wallet.grpc.WalletServiceGrpc;
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
  public DebitResponse debit(WalletDebitCommand command) {
    return walletService.debit(DebitRequest.newBuilder()
        .setPaymentId(command.paymentId() != null ? command.paymentId() : 0L)
        .setSourceUserId(command.sourceUserId() != null ? command.sourceUserId() : 0L)
        .setTargetUserId(command.targetUserId() != null ? command.targetUserId() : 0L)
        .setAmount(command.amount() != null ? command.amount() : ZERO_AMOUNT)
        .setCurrency(command.currency() != null ? command.currency() : EMPTY_STRING).build());
  }
}

package com.example.payment.infrastructure.external.adapter;

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
  public DebitResponse debit(Long paymentId, Long sourceUserId, Long targetUserId, String amount,
      String currency) {
    return walletService
        .debit(DebitRequest.newBuilder().setPaymentId(paymentId != null ? paymentId : 0L)
            .setSourceUserId(sourceUserId != null ? sourceUserId : 0L)
            .setTargetUserId(targetUserId != null ? targetUserId : 0L)
            .setAmount(amount != null ? amount : ZERO_AMOUNT)
            .setCurrency(currency != null ? currency : EMPTY_STRING).build());
  }
}

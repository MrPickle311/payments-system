package com.example.payments.wallet.api;

import com.example.payments.wallet.application.WalletService;
import com.example.payments.wallet.grpc.DebitRequest;
import com.example.payments.wallet.grpc.DebitResponse;
import com.example.payments.wallet.grpc.WalletServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class WalletGrpcController extends WalletServiceGrpc.WalletServiceImplBase {

    private final WalletService walletService;

    @Override
    public void debit(DebitRequest request, StreamObserver<DebitResponse> responseObserver) {
        log.info(
                "[WalletGrpc] Debit paymentId={} from userId={} to userId={} amount={} {}",
                request.getPaymentId(),
                request.getSourceUserId(),
                request.getTargetUserId(),
                request.getAmount(),
                request.getCurrency());

        String status = walletService.debitBetweenUsers(request);

        responseObserver.onNext(DebitResponse.newBuilder().setStatus(status).build());
        responseObserver.onCompleted();
    }
}

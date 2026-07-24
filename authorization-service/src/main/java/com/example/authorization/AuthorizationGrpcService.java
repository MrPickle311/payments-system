package com.example.authorization;

import com.example.payments.authorization.grpc.AuthorizationRequest;
import com.example.payments.authorization.grpc.AuthorizationResponse;
import com.example.payments.authorization.grpc.AuthorizationServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
public class AuthorizationGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

    @Override
    public void authorize(AuthorizationRequest request, StreamObserver<AuthorizationResponse> responseObserver) {
        log.info("Simulating authorization for payment {}", request.getPaymentId());
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        AuthorizationResponse response =
                AuthorizationResponse.newBuilder().setSuccess(true).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reverseAuthorization(
            AuthorizationRequest request, StreamObserver<AuthorizationResponse> responseObserver) {
        log.info("Reversing authorization for payment {}", request.getPaymentId());
        AuthorizationResponse response =
                AuthorizationResponse.newBuilder().setSuccess(true).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

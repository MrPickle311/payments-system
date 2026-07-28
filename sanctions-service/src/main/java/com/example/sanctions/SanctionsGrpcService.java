package com.example.sanctions;

import com.example.payments.sanctions.grpc.GetSanctionedUserIdsRequest;
import com.example.payments.sanctions.grpc.SanctionedUserIdsResponse;
import com.example.payments.sanctions.grpc.SanctionsServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Set;

@Slf4j
@GrpcService
public class SanctionsGrpcService extends SanctionsServiceGrpc.SanctionsServiceImplBase {

    private static final Set<Long> BANNED_USER_IDS = Set.of(999L, 1337L, 666L);

    @Override
    public void getSanctionedUserIds(
            GetSanctionedUserIdsRequest req, StreamObserver<SanctionedUserIdsResponse> observer) {
        log.info("[Sanctions] Returning static list of sanctioned user IDs");
        observer.onNext(SanctionedUserIdsResponse.newBuilder()
                .addAllSanctionedUserIds(BANNED_USER_IDS)
                .build());
        observer.onCompleted();
    }

}

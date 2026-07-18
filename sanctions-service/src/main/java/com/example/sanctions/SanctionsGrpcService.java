package com.example.sanctions;

import com.example.payments.sanctions.grpc.SanctionsRequest;
import com.example.payments.sanctions.grpc.SanctionsResponse;
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
  public void checkSanctions(SanctionsRequest req, StreamObserver<SanctionsResponse> observer) {
    long src = req.getSourceUserId();
    long tgt = req.getTargetUserId();
    log.info("[Sanctions] Check paymentId={} source={} target={}", req.getPaymentId(), src, tgt);
    boolean blocked = BANNED_USER_IDS.contains(src) || BANNED_USER_IDS.contains(tgt);
    if (blocked) {
      log.warn("[Sanctions] Sanctions hit! source={} target={}", src, tgt);
    }
    observer.onNext(SanctionsResponse.newBuilder().setSuccess(!blocked).build());
    observer.onCompleted();
  }
}

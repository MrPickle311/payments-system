package com.example.fee.fee.api;

import com.example.fee.fee.application.FeeCalculationService;
import com.example.fee.fee.domain.FeeBreakdown;
import com.example.payments.common.sharedkernel.Money;
import com.example.payments.fee.grpc.FeeRequest;
import com.example.payments.fee.grpc.FeeResponse;
import com.example.payments.fee.grpc.FeeServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FeeGrpcService extends FeeServiceGrpc.FeeServiceImplBase {

    private final FeeCalculationService feeCalculationService;

    @Override
    public void calculateFee(FeeRequest req, StreamObserver<FeeResponse> observer) {
        long payId = req.getPaymentId();
        log.info("[FeeService] CalculateFee for paymentId={}", payId);
        try {
            Money gross = Money.of(new BigDecimal(req.getAmount()), req.getCurrency());
            FeeBreakdown breakdown = feeCalculationService.calculate(gross);
            observer.onNext(buildResponse(breakdown, req.getCurrency()));
        } catch (Exception e) {
            log.error("[FeeService] Fee calculation error for paymentId={}", payId, e);
            observer.onNext(FeeResponse.newBuilder().setSuccess(false).build());
        }
        observer.onCompleted();
    }

    private FeeResponse buildResponse(FeeBreakdown breakdown, String currency) {
        return FeeResponse.newBuilder()
                .setSuccess(true)
                .setFeeAmount(breakdown.totalFee().amount().toPlainString())
                .setCurrency(currency)
                .build();
    }
}

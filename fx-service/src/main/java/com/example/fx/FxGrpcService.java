package com.example.fx;

import com.example.payments.fx.grpc.FxRequest;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.fx.grpc.FxServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@GrpcService
public class FxGrpcService extends FxServiceGrpc.FxServiceImplBase {

  private static final int SCALE = 4;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private static final Map<String, BigDecimal> RATES_VS_USD =
      Map.ofEntries(Map.entry("USD", BigDecimal.ONE), Map.entry("EUR", new BigDecimal("0.92")),
          Map.entry("GBP", new BigDecimal("0.79")), Map.entry("PLN", new BigDecimal("3.97")),
          Map.entry("CHF", new BigDecimal("0.90")), Map.entry("JPY", new BigDecimal("157.50")),
          Map.entry("CAD", new BigDecimal("1.37")), Map.entry("AUD", new BigDecimal("1.53")));

  @Override
  public void processFx(FxRequest req, StreamObserver<FxResponse> observer) {
    long payId = req.getPaymentId();
    log.debug("[FxService] ProcessFx paymentId={}", payId);
    try {
      BigDecimal amount = new BigDecimal(req.getAmount());
      observer.onNext(handleConversion(req.getSourceCurrency().toUpperCase(),
          req.getTargetCurrency().toUpperCase(), amount));
    } catch (Exception e) {
      log.error("[FxService] Error processing FX for paymentId={}", payId, e);
      observer.onNext(FxResponse.newBuilder().setSuccess(false).build());
    }
    observer.onCompleted();
  }

  private FxResponse handleConversion(String sourceCurrency, String targetCurrency,
      BigDecimal amount) {
    if (sourceCurrency.equals(targetCurrency)) {
      return buildSuccessResponse(amount.setScale(SCALE, ROUNDING), targetCurrency, BigDecimal.ONE);
    }
    BigDecimal srcRate = RATES_VS_USD.get(sourceCurrency);
    BigDecimal tgtRate = RATES_VS_USD.get(targetCurrency);
    if (srcRate == null || tgtRate == null) {
      return FxResponse.newBuilder().setSuccess(false).build();
    }
    BigDecimal rate = tgtRate.divide(srcRate, MathContext.DECIMAL128).setScale(SCALE, ROUNDING);
    BigDecimal convertedAmount = amount.multiply(rate).setScale(SCALE, ROUNDING);
    return buildSuccessResponse(convertedAmount, targetCurrency, rate);
  }

  private FxResponse buildSuccessResponse(BigDecimal amount, String currency, BigDecimal rate) {
    return FxResponse.newBuilder().setSuccess(true).setConvertedAmount(amount.toPlainString())
        .setTargetCurrency(currency).setExchangeRate(rate.toPlainString()).build();
  }
}

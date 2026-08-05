package com.example.payment.infrastructure.external.adapter;

import static java.math.MathContext.DECIMAL128;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.example.payment.domain.gateway.FxGateway;
import com.example.payments.fx.grpc.FxRequest;
import com.example.payments.fx.grpc.FxResponse;
import com.example.payments.fx.grpc.FxServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Deadline;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcFxGatewayAdapter implements FxGateway {

    private static final String FX_CONVERSION_RATES_KEY = "fx-conversion-rates";
    private static final String ZERO_AMOUNT = "0";
    private static final String EMPTY_STRING = "";
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @GrpcClient("fx-service")
    private final FxServiceGrpc.FxServiceBlockingStub fxService;

    private final StringRedisTemplate redisTemplate;

    @Override
    @CircuitBreaker(name = "fxService", fallbackMethod = "processFxFallback")
    @Retry(name = "fxService", fallbackMethod = "processFxFallback")
    public FxResponse processFx(Long paymentId, String amount, String sourceCurrency, String targetCurrency) {
        try {
            return fxService
                    .withDeadline(Deadline.after(3, SECONDS))
                    .processFx(FxRequest.newBuilder()
                            .setPaymentId(paymentId != null ? paymentId : 0L)
                            .setAmount(amount != null ? amount : ZERO_AMOUNT)
                            .setSourceCurrency(sourceCurrency != null ? sourceCurrency : EMPTY_STRING)
                            .setTargetCurrency(getTargetCurrency(sourceCurrency, targetCurrency))
                            .build());
        } catch (Exception ex) {
            log.warn(
                    "[GrpcFxGateway] Call to fx-service failed for paymentId={}, attempting Redis fallback: {}",
                    paymentId,
                    ex.getMessage());
            return calculateFxFromRedisFallback(amount, sourceCurrency, targetCurrency);
        }
    }

    @SuppressWarnings("unused")
    public FxResponse processFxFallback(
            Long paymentId, String amount, String sourceCurrency, String targetCurrency, Throwable t) {
        log.warn(
                "[GrpcFxGateway] Resilience4j fallback triggered for paymentId={}, using Redis rates table: {}",
                paymentId,
                t.getMessage());
        return calculateFxFromRedisFallback(amount, sourceCurrency, targetCurrency);
    }

    private FxResponse calculateFxFromRedisFallback(String amountStr, String srcCurRaw, String tgtCurRaw) {
        String sourceCurrency = srcCurRaw != null ? srcCurRaw.toUpperCase() : EMPTY_STRING;
        String targetCurrency = getTargetCurrency(sourceCurrency, tgtCurRaw).toUpperCase();

        try {
            Map<Object, Object> ratesMap = redisTemplate.opsForHash().entries(FX_CONVERSION_RATES_KEY);
            if (ratesMap.isEmpty()) {
                log.error(
                        "[GrpcFxGateway] Redis conversion rates table '{}' is empty/unavailable",
                        FX_CONVERSION_RATES_KEY);
                return FxResponse.newBuilder().setSuccess(false).build();
            }

            var amount = new BigDecimal(amountStr != null ? amountStr : ZERO_AMOUNT);
            if (sourceCurrency.equals(targetCurrency)) {
                return buildSuccessResponse(amount.setScale(SCALE, ROUNDING), targetCurrency, BigDecimal.ONE);
            }

            var sourceRateRaw = (String) ratesMap.get(sourceCurrency);
            var targetRateRaw = (String) ratesMap.get(targetCurrency);
            if (sourceRateRaw == null || targetRateRaw == null) {
                log.error(
                        "[GrpcFxGateway] Missing rate in Redis for currencies src={} tgt={}",
                        sourceCurrency,
                        targetCurrency);
                return FxResponse.newBuilder().setSuccess(false).build();
            }

            var sourceRate = new BigDecimal(sourceRateRaw);
            var targetRate = new BigDecimal(targetRateRaw);
            BigDecimal rate = targetRate.divide(sourceRate, DECIMAL128).setScale(SCALE, ROUNDING);
            BigDecimal convertedAmount = amount.multiply(rate).setScale(SCALE, ROUNDING);

            log.info(
                    "[GrpcFxGateway] Fallback FX calculation successful src={} tgt={} amount={} result={}",
                    sourceCurrency,
                    targetCurrency,
                    amountStr,
                    convertedAmount);
            return buildSuccessResponse(convertedAmount, targetCurrency, rate);
        } catch (Exception ex) {
            log.error("[GrpcFxGateway] Error during fallback FX calculation", ex);
            return FxResponse.newBuilder().setSuccess(false).build();
        }
    }

    private FxResponse buildSuccessResponse(BigDecimal amount, String currency, BigDecimal rate) {
        return FxResponse.newBuilder()
                .setSuccess(true)
                .setConvertedAmount(amount.toPlainString())
                .setTargetCurrency(currency)
                .setExchangeRate(rate.toPlainString())
                .build();
    }

    private static String getTargetCurrency(String sourceCurrency, String targetCurrency) {
        if (targetCurrency != null && !targetCurrency.isBlank()) {
            return targetCurrency;
        }
        return sourceCurrency != null ? sourceCurrency : EMPTY_STRING;
    }
}

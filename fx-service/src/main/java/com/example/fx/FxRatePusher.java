package com.example.fx;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxRatePusher {

    public static final String FX_CONVERSION_RATES_KEY = "fx-conversion-rates";

    private final StringRedisTemplate redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 60000)
    public void pushRatesToRedis() {
        try {
            Map<String, String> stringRates = FxGrpcService.RATES_VS_USD.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, e -> e.getValue().toPlainString()));
            redisTemplate.opsForHash().putAll(FX_CONVERSION_RATES_KEY, stringRates);
            log.info(
                    "[FxRatePusher] Pushed conversion rates table from FxGrpcService to Redis hash '{}'",
                    FX_CONVERSION_RATES_KEY);
        } catch (Exception ex) {
            log.warn("[FxRatePusher] Failed to push rates table to Redis: {}", ex.getMessage(), ex);
        }
    }
}

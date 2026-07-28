package com.example.payment.infrastructure.external.adapter;

import com.example.payment.domain.gateway.SanctionsGateway;
import com.example.payments.sanctions.grpc.GetSanctionedUserIdsRequest;
import com.example.payments.sanctions.grpc.SanctionedUserIdsResponse;
import com.example.payments.sanctions.grpc.SanctionsServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcSanctionsGatewayAdapter implements SanctionsGateway {

    private static final String CACHE_NAME = "sanctioned-user-ids";
    private static final String CACHE_KEY = "all";

    @GrpcClient("sanctions-service")
    private final SanctionsServiceGrpc.SanctionsServiceBlockingStub sanctionsService;

    private final CacheManager cacheManager;

    @Override
    @CircuitBreaker(name = "sanctionsService")
    @Retry(name = "sanctionsService")
    public boolean checkSanctions(Long paymentId, Long sourceUserId, Long targetUserId) {
        List<Long> bannedUserIds = fetchSanctionedUserIdsWithCacheFallback();
        boolean isBlocked = (sourceUserId != null && bannedUserIds.contains(sourceUserId))
                || (targetUserId != null && bannedUserIds.contains(targetUserId));
        return !isBlocked;
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchSanctionedUserIdsWithCacheFallback() {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(CACHE_KEY);
                if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                    log.debug("[SanctionsCache] Cache hit for sanctioned user IDs");
                    return (List<Long>) cached;
                }
            }
        } catch (Exception ex) {
            log.warn("[SanctionsCache] Cache error on read, falling back directly to sanctions-service: {}", ex.getMessage());
        }

        log.info("[SanctionsCache] Fetching sanctioned user IDs from sanctions-service");
        List<Long> freshList = callSanctionsService();
        updateCache(freshList);
        return freshList;
    }

    private void updateCache(List<Long> freshList) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null && freshList != null && !freshList.isEmpty()) {
                cache.put(CACHE_KEY, freshList);
                log.debug("[SanctionsCache] Updated cache for sanctioned user IDs");
            }
        } catch (Exception ex) {
            log.warn("[SanctionsCache] Cache error on write: {}", ex.getMessage());
        }
    }

    private List<Long> callSanctionsService() {
        SanctionedUserIdsResponse response = sanctionsService
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getSanctionedUserIds(GetSanctionedUserIdsRequest.newBuilder().build());
        return response.getSanctionedUserIdsList();
    }
}

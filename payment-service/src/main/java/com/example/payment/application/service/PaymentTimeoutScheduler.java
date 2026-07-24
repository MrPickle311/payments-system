package com.example.payment.application.service;

import com.example.payment.infrastructure.persistence.PaymentJpaEntity;
import com.example.payment.infrastructure.persistence.SpringDataPaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private static final int STUCK_THRESHOLD_MINUTES = 10;
    private static final int BATCH_SIZE = 20;

    private final SpringDataPaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    /**
     * Sweeps payments stuck in PROCESSING for more than STUCK_THRESHOLD_MINUTES.
     * Uses SKIP LOCKED so multiple pods each grab a disjoint batch — no distributed lock needed.
     * fixedDelay (not fixedRate) ensures runs never overlap within the same pod.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweepStuckPayments() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<PaymentJpaEntity> stuck = paymentRepository.findStuckPaymentsForUpdate(threshold, BATCH_SIZE);
        if (stuck.isEmpty()) {
            return;
        }

        log.warn("[Sweeper] Found {} payment(s) stuck in PROCESSING, forcing FAIL", stuck.size());
        stuck.forEach(p -> {
            try {
                paymentService.forceTimeout(p.getId());
            } catch (Exception e) {
                log.error("[Sweeper] Failed to timeout paymentId={}", p.getId(), e);
            }
        });
    }
}

package com.example.payments.export.job;

import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("retry-manager")
@RequiredArgsConstructor
public class FailedPaymentRetryLauncher {

    private final PaymentExportStagingRepository stagingRepository;
    private final ExportProperties exportProperties;
    private final Clock clock;

    @Scheduled(cron = "${export.retry.schedule:0 */5 * * * *}")
    @Transactional
    public void retryFailedPayments() {
        LocalDateTime since = LocalDate.now(clock)
                .minusMonths(exportProperties.getLookbackMonths())
                .withDayOfMonth(1)
                .atStartOfDay();

        int reset = stagingRepository.resetFailedToPending(exportProperties.getMaxRetryCount(), since);

        if (reset == 0) {
            log.info("[RetryLauncher] No eligible FAILED rows to retry.");
            return;
        }

        log.info("[RetryLauncher] Reset {} FAILED → PENDING. Manager pods will pick them up shortly.", reset);
    }
}

package com.example.payments.export.job;

import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
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
    private final JobLauncher jobLauncher;
    private final Job exportLedgerJob;
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

        log.info("[RetryLauncher] Reset {} FAILED → PENDING, triggering export job.", reset);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", clock.millis())
                    .addString("triggeredBy", "retry-manager")
                    .toJobParameters();
            jobLauncher.run(exportLedgerJob, params); // TODO: why this launcher use this job ?
        } catch (Exception e) {
            log.error("[RetryLauncher] Job launch failed: {}", e.getMessage());
        }
    }
}

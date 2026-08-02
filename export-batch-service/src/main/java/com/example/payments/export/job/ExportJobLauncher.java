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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.example.payments.export.staging.PaymentExportStaging.ExportStatus.PENDING;

@Slf4j
@Component
@Profile("manager")
@RequiredArgsConstructor
public class ExportJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job exportLedgerJob;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final PaymentExportStagingRepository stagingRepository;
    private final ExportProperties exportProperties;

    @Scheduled(cron = "${export.schedule:0/30 * * * * *}")
    public void launchJob() {
        LocalDateTime since = LocalDate.now(clock)
                .minusMonths(exportProperties.getLookbackMonths())
                .withDayOfMonth(1)
                .atStartOfDay();

        if (!stagingRepository.existsByStatusAndCreatedAtGreaterThanEqual(
                PENDING, since)) {
            log.info("[JobLauncher] No PENDING events found. Skipping job execution.");
            return;
        }

        log.info("[JobLauncher] Triggering exportLedgerJob...");
        try {
            JobParameters params =
                    new JobParametersBuilder().addLong("time", clock.millis()).toJobParameters();
            jobLauncher.run(exportLedgerJob, params);
        } catch (Exception exception) {
            log.error("[JobLauncher] Job failed to start: {}", exception.getMessage());
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runPartmanMaintenance() {
        log.debug("[JobLauncher] Running pg_partman maintenance...");
        jdbcTemplate.execute("SELECT partman.run_maintenance('public.payment_export_staging')");
    }
}

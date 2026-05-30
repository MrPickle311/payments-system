package com.example.payments.export.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manager")
@RequiredArgsConstructor
public class ExportJobLauncher {

  private final JobLauncher jobLauncher;
  private final Job exportLedgerJob;

  @Scheduled(cron = "${export.schedule:0/30 * * * * *}")
  public void launchJob() {
    log.info("[JobLauncher] Triggering exportLedgerJob...");
    try {
      JobParameters params =
          new JobParametersBuilder().addLong("time", System.currentTimeMillis()).toJobParameters();
      jobLauncher.run(exportLedgerJob, params);
    } catch (Exception e) {
      log.error("[JobLauncher] Job failed to start: {}", e.getMessage());
    }
  }
}

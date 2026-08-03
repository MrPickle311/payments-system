package com.example.payments.export.config;

import static com.example.payments.export.config.ExportConstants.WORKER_STEP_NAME;

import com.example.payments.export.job.PaymentIdTrackingListener;
import com.example.payments.export.job.StagingTablePartitioner;
import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import com.example.payments.export.writer.RegulatoryApiWriter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Configuration
@Profile("manager")
@RequiredArgsConstructor
public class LocalBatchConfig {

    public static final String EXPORT_LEDGER_JOB_NAME = "exportLedgerJob";
    public static final String MANAGER_STEP_NAME = "managerStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcPagingItemReader<PaymentExportStaging> stagingItemReader;
    private final RegulatoryApiWriter regulatoryApiWriter;
    private final ExportProperties exportProperties;
    private final PaymentExportStagingRepository stagingRepository;
    private final Clock clock;

    @Bean
    public Job exportLedgerJob(Step managerStep) {
        return new JobBuilder(EXPORT_LEDGER_JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(managerStep)
                .build();
    }

    @Bean
    public Step managerStep(TaskExecutorPartitionHandler partitionHandler, Partitioner partitioner) {
        return new StepBuilder(MANAGER_STEP_NAME, jobRepository)
                .partitioner(WORKER_STEP_NAME, partitioner)
                .partitionHandler(partitionHandler)
                .gridSize(exportProperties.getGridSize())
                .build();
    }

    @Bean
    public Partitioner partitioner() {
        return new StagingTablePartitioner(stagingRepository, exportProperties, clock);
    }

    @Bean
    public TaskExecutorPartitionHandler partitionHandler(Step workerStep, TaskExecutor taskExecutor) {
        var handler = new TaskExecutorPartitionHandler();
        handler.setGridSize(exportProperties.getGridSize());
        handler.setTaskExecutor(taskExecutor);
        handler.setStep(workerStep);
        return handler;
    }

    @Bean
    public TaskExecutor taskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(exportProperties.getGridSize());
        executor.setMaxPoolSize(exportProperties.getGridSize());
        executor.setThreadNamePrefix("partition_thread");
        executor.initialize();
        return executor;
    }

    @Bean
    public Step workerStep(PaymentIdTrackingListener trackingListener) {
        return new StepBuilder(WORKER_STEP_NAME, jobRepository)
                .<PaymentExportStaging, PaymentExportStaging>chunk(exportProperties.getBatchSize(), transactionManager)
                .reader(stagingItemReader)
                .processor(item -> item)
                .writer(regulatoryApiWriter)
                .listener((ItemWriteListener<? super PaymentExportStaging>) trackingListener)
                .listener((StepExecutionListener) trackingListener)
                .faultTolerant()
                .retryLimit(3)
                .retry(ResourceAccessException.class)
                .retry(HttpServerErrorException.class)
                .build();
    }
}

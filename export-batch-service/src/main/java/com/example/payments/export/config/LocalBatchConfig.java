package com.example.payments.export.config;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.job.OffsetRangePartitioner;
import com.example.payments.export.writer.RegulatoryApiWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LocalBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final KafkaItemReader<String, String> kafkaItemReader;
    private final RegulatoryApiWriter regulatoryApiWriter;
    private final ObjectMapper objectMapper;

    @Value("${export.grid-size:5}")
    private int gridSize;

    @Value("${export.batch-size:500}")
    private int batchSize;

    @Bean
    public Job exportLedgerJob(Step managerStep) {
        return new JobBuilder("exportLedgerJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(managerStep)
                .build();
    }

    @Bean
    public Step managerStep(TaskExecutorPartitionHandler partitionHandler, Partitioner partitioner) {
        return new StepBuilder("managerStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .partitionHandler(partitionHandler)
                .gridSize(gridSize)
                .build();
    }

    @Bean
    public Partitioner partitioner() {
        return new OffsetRangePartitioner();
    }

    @Bean
    public TaskExecutorPartitionHandler partitionHandler(Step workerStep, TaskExecutor taskExecutor) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setGridSize(gridSize);
        handler.setTaskExecutor(taskExecutor);
        handler.setStep(workerStep);
        return handler;
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(gridSize);
        executor.setMaxPoolSize(gridSize);
        executor.setThreadNamePrefix("partition_thread");
        executor.initialize();
        return executor;
    }

    @Bean
    public Step workerStep() {
        return new StepBuilder("workerStep", jobRepository)
                .<String, LedgerEvent>chunk(batchSize, transactionManager)
                .reader(kafkaItemReader)
                .processor(eventProcessor())
                .writer(regulatoryApiWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(ResourceAccessException.class)
                .retry(HttpServerErrorException.class)
                .build();
    }

    @Bean
    public ItemProcessor<String, LedgerEvent> eventProcessor() {
        return item -> {
            try {
                LedgerEvent event = objectMapper.readValue(item, LedgerEvent.class);
                log.debug("[WorkerProcessor] Parsed event for paymentId: {}", event.getPaymentId());
                return event;
            } catch (Exception e) {
                log.error("[WorkerProcessor] Failed to parse JSON: {}", item);
                return null;
            }
        };
    }
}

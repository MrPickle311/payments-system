package com.example.payments.export.config;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.writer.RegulatoryApiWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.partition.BeanFactoryStepLocator;
import org.springframework.batch.integration.partition.StepExecutionRequestHandler;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@Profile("worker")
@RequiredArgsConstructor
public class WorkerBatchConfig {

    private final JobRepository jobRepository;
    private final org.springframework.batch.core.explore.JobExplorer jobExplorer;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final PlatformTransactionManager transactionManager;
    private final KafkaItemReader<String, String> kafkaItemReader;
    private final RegulatoryApiWriter regulatoryApiWriter;
    private final ObjectMapper objectMapper;

    @Value("${export.batch-size:500}")
    private int batchSize;

    @Bean
    public Step workerStep() {
        return new StepBuilder("workerStep", jobRepository)
                .<String, LedgerEvent>chunk(batchSize, transactionManager)
                .reader(kafkaItemReader)
                .processor(eventProcessor())
                .writer(regulatoryApiWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(org.springframework.web.client.ResourceAccessException.class)
                .retry(org.springframework.web.client.HttpServerErrorException.class)
                .build();
    }

    @Bean
    @ServiceActivator(inputChannel = "inboundRequests", outputChannel = "outboundReplies")
    public StepExecutionRequestHandler stepExecutionRequestHandler() {
        StepExecutionRequestHandler handler = new StepExecutionRequestHandler();
        handler.setJobExplorer(jobExplorer);

        var stepLocator = new BeanFactoryStepLocator();
        stepLocator.setBeanFactory(applicationContext);
        
        handler.setStepLocator(stepLocator);
        return handler;
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

package com.example.payments.export.config;

import com.example.payments.export.job.OffsetRangePartitioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.partition.MessageChannelPartitionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;

@Slf4j
@Configuration
@Profile("manager")
public class ManagerBatchConfig {

    private final JobRepository jobRepository;
    private final MessageChannel outboundRequests;
    private final PollableChannel inboundReplies;

    public ManagerBatchConfig(JobRepository jobRepository,
                             @Qualifier("outboundRequests") MessageChannel outboundRequests,
                             @Qualifier("inboundReplies") PollableChannel inboundReplies) {
        this.jobRepository = jobRepository;
        this.outboundRequests = outboundRequests;
        this.inboundReplies = inboundReplies;
    }

    @Value("${export.grid-size:5}")
    private int gridSize;

    @Bean
    public Job exportLedgerJob(Step managerStep) {
        return new JobBuilder("exportLedgerJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(managerStep)
                .build();
    }

    @Bean
    public Step managerStep(PartitionHandler partitionHandler) {
        return new StepBuilder("managerStep", jobRepository)
                .partitioner("workerStep", new OffsetRangePartitioner())
                .partitionHandler(partitionHandler)
                .gridSize(gridSize)
                .build();
    }

    @Bean
    public MessageChannelPartitionHandler partitionHandler() {
        MessageChannelPartitionHandler handler = new MessageChannelPartitionHandler();
        handler.setStepName("workerStep");
        handler.setReplyChannel(inboundReplies);
        handler.setMessagingOperations(new MessagingTemplate(outboundRequests));
        handler.setPollInterval(5000L);
        handler.setTimeout(60000L);
        return handler;
    }
}

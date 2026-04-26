package com.example.payments.export.config;

import com.example.payments.export.dto.StepPartitionReplyDto;
import com.example.payments.export.dto.StepPartitionRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;

import java.util.Collections;

/**
 * Wires Spring Batch Remote Partitioning channels to Kafka topics using lightweight JSON DTOs.
 *
 * <p>Instead of serializing the full Spring Batch internal objects (which have circular references),
 * we extract only the relevant fields into {@link StepPartitionRequestDto} and
 * {@link StepPartitionReplyDto}, transmit them as plain JSON strings over Kafka, and reconstruct
 * the Spring Batch objects on each side using the JobExplorer / JobRepository.
 *
 * <h3>Manager side</h3>
 * <pre>
 *   StepExecutionRequest → StepPartitionRequestDto (JSON) → Kafka export-batch-requests
 *   Kafka export-batch-replies → StepPartitionReplyDto (JSON) → StepExecution (from DB) → inboundReplies
 * </pre>
 *
 * <h3>Worker side</h3>
 * <pre>
 *   Kafka export-batch-requests → StepPartitionRequestDto (JSON) → StepExecutionRequest → inboundRequests
 *   StepExecution → StepPartitionReplyDto (JSON) → Kafka export-batch-replies
 * </pre>
 */
@Slf4j
@Configuration
@EnableBatchIntegration
public class IntegrationConfig {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConsumerFactory<String, String> consumerFactory;
    private final ObjectMapper objectMapper;

    @Value("${export.batch.requests-topic:export-batch-requests}")
    private String requestsTopic;

    @Value("${export.batch.replies-topic:export-batch-replies}")
    private String repliesTopic;

    public IntegrationConfig(KafkaTemplate<String, String> kafkaTemplate,
                             ConsumerFactory<String, String> consumerFactory,
                             ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.consumerFactory = consumerFactory;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------------------
    // Channels (shared between Manager and Worker profiles)
    // ---------------------------------------------------------------------------

    @Bean
    public MessageChannel outboundRequests() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel inboundRequests() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel outboundReplies() {
        return new DirectChannel();
    }

    @Bean
    public PollableChannel inboundReplies() {
        return new QueueChannel();
    }

    // ---------------------------------------------------------------------------
    // Manager: Outbound — StepExecutionRequest → JSON → Kafka
    // ---------------------------------------------------------------------------

    @Bean
    @Profile("manager")
    public IntegrationFlow outboundRequestsFlow() {
        return IntegrationFlow.from(outboundRequests())
                .transform(payload -> {
                    StepExecutionRequest request = (StepExecutionRequest) payload;
                    StepPartitionRequestDto dto = new StepPartitionRequestDto(
                            request.getStepName(),
                            request.getJobExecutionId(),
                            request.getStepExecutionId()
                    );
                    return toJson(dto);
                })
                .handle(Kafka.outboundChannelAdapter(kafkaTemplate).topic(requestsTopic))
                .get();
    }

    // ---------------------------------------------------------------------------
    // Manager: Inbound — Kafka → JSON → StepExecution (from DB) → inboundReplies
    // ---------------------------------------------------------------------------

    @Bean
    @Profile("manager")
    public IntegrationFlow inboundRepliesFlow(JobExplorer jobExplorer) {
        return IntegrationFlow
                .from(Kafka.messageDrivenChannelAdapter(consumerFactory, repliesTopic))
                .transform(payload -> {
                    StepPartitionReplyDto dto = fromJson((String) payload, StepPartitionReplyDto.class);

                    // Look up the authoritative StepExecution from the JobRepository database.
                    // This is the same object the Manager created when it launched the partition.
                    StepExecution stepExecution = jobExplorer.getStepExecution(
                            dto.getJobExecutionId(), dto.getStepExecutionId());

                    if (stepExecution == null) {
                        throw new IllegalStateException(
                                "No StepExecution found for id=" + dto.getStepExecutionId());
                    }

                    // Apply the worker's reported status and counts.
                    stepExecution.setStatus(BatchStatus.valueOf(dto.getBatchStatus()));
                    stepExecution.setExitStatus(new ExitStatus(dto.getExitCode(), dto.getExitDescription()));
                    stepExecution.setReadCount(dto.getReadCount());
                    stepExecution.setWriteCount(dto.getWriteCount());
                    stepExecution.setFilterCount(dto.getFilterCount());
                    stepExecution.setReadSkipCount(dto.getReadSkipCount());
                    stepExecution.setWriteSkipCount(dto.getWriteSkipCount());
                    stepExecution.setProcessSkipCount(dto.getProcessSkipCount());
                    stepExecution.setRollbackCount(dto.getRollbackCount());
                    stepExecution.setCommitCount(dto.getCommitCount());

                    log.info("[Manager] Received reply for stepExecutionId={} status={}",
                            dto.getStepExecutionId(), dto.getBatchStatus());

                    // MessageChannelPartitionHandler expects a Collection<StepExecution>
                    return Collections.singletonList(stepExecution);
                })
                .channel(inboundReplies())
                .get();
    }

    // ---------------------------------------------------------------------------
    // Worker: Inbound — Kafka → JSON → StepExecutionRequest → inboundRequests
    // ---------------------------------------------------------------------------

    @Bean
    @Profile("worker")
    public IntegrationFlow inboundRequestsFlow() {
        return IntegrationFlow
                .from(Kafka.messageDrivenChannelAdapter(consumerFactory, requestsTopic))
                .transform(payload -> {
                    StepPartitionRequestDto dto = fromJson((String) payload, StepPartitionRequestDto.class);

                    log.info("[Worker] Received request for stepName={} jobExecutionId={} stepExecutionId={}",
                            dto.getStepName(), dto.getJobExecutionId(), dto.getStepExecutionId());

                    return new StepExecutionRequest(
                            dto.getStepName(),
                            dto.getJobExecutionId(),
                            dto.getStepExecutionId()
                    );
                })
                .channel(inboundRequests())
                .get();
    }

    // ---------------------------------------------------------------------------
    // Worker: Outbound — StepExecution → JSON DTO → Kafka
    // ---------------------------------------------------------------------------

    @Bean
    @Profile("worker")
    public IntegrationFlow outboundRepliesFlow() {
        return IntegrationFlow.from(outboundReplies())
                .transform(payload -> {
                    StepExecution stepExecution = (StepExecution) payload;

                    StepPartitionReplyDto dto = new StepPartitionReplyDto(
                            stepExecution.getId(),
                            stepExecution.getJobExecution().getId(),
                            stepExecution.getStatus().name(),
                            stepExecution.getExitStatus().getExitCode(),
                            stepExecution.getExitStatus().getExitDescription(),
                            stepExecution.getReadCount(),
                            stepExecution.getWriteCount(),
                            stepExecution.getFilterCount(),
                            stepExecution.getReadSkipCount(),
                            stepExecution.getWriteSkipCount(),
                            stepExecution.getProcessSkipCount(),
                            stepExecution.getRollbackCount(),
                            stepExecution.getCommitCount()
                    );

                    log.info("[Worker] Sending reply for stepExecutionId={} status={}",
                            dto.getStepExecutionId(), dto.getBatchStatus());

                    return toJson(dto);
                })
                .handle(Kafka.outboundChannelAdapter(kafkaTemplate).topic(repliesTopic))
                .get();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed for " + obj.getClass().getSimpleName(), e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed into " + type.getSimpleName(), e);
        }
    }
}

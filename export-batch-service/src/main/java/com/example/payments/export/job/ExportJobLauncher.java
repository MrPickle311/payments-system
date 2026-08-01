package com.example.payments.export.job;

import com.example.payments.export.config.ExportProperties;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("manager")
@RequiredArgsConstructor
public class ExportJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job exportLedgerJob;
    private final JobExplorer jobExplorer;
    private final ConsumerFactory<String, String> consumerFactory;
    private final ExportProperties exportProperties;

    @Scheduled(cron = "${export.schedule:0/30 * * * * *}")
    public void launchJob() {
        if (!hasPendingMessages()) {
            log.debug(
                    "[JobLauncher] No new messages on topic {}, skipping job execution.", exportProperties.getTopic());
            return;
        }

        log.info("[JobLauncher] Triggering exportLedgerJob...");
        try {
            JobParameters params = determineJobParameters();
            jobLauncher.run(exportLedgerJob, params);
        } catch (Exception exception) {
            log.error("[JobLauncher] Job failed to start: {}", exception.getMessage());
        }
    }

    private JobParameters determineJobParameters() {
        log.info("[JobLauncher] Starting fresh job execution with explicit offsets.");
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis());
                
        int gridSize = exportProperties.getGridSize();
        for (int i = 0; i < gridSize; i++) {
            builder.addLong("offset-" + i, getLastProcessedOffset(i));
        }
        
        return builder.toJobParameters();
    }

    private boolean hasPendingMessages() {
        String topic = exportProperties.getTopic();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("export-batch-checker", "export-batch-checker-client")) {
            List<org.apache.kafka.common.PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return false;
            }

            List<TopicPartition> partitions = partitionInfos.stream()
                    .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                    .toList();

            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            for (TopicPartition tp : partitions) {
                long endOffset = endOffsets.getOrDefault(tp, 0L);
                long lastProcessed = getLastProcessedOffset(tp.partition());
                if (endOffset > lastProcessed) {
                    log.debug(
                            "[JobLauncher] Partition {} has pending messages: lastProcessed={}, end={}",
                            tp.partition(),
                            lastProcessed,
                            endOffset);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[JobLauncher] Could not check offsets, proceeding with job: {}", e.getMessage());
            return true; // Default to true to be safe
        }
        return false;
    }

    /**
     * Returns the highest offset processed for the given partition.
     * Reads from the KafkaItemReader's saved state in the step ExecutionContext
     * across recent job executions (including failed ones, as chunks may have committed).
     */
    private long getLastProcessedOffset(int partitionId) {
        List<JobInstance> instances = jobExplorer.findJobInstancesByJobName(exportLedgerJob.getName(), 0, 10);
        for (JobInstance instance : instances) {
            List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
            if (executions.isEmpty()) continue;
            JobExecution execution = executions.get(0);
            
            long contextOffset = execution.getStepExecutions().stream()
                .map(se -> {
                    Object offsets = se.getExecutionContext().get("topic.partition.offsets");
                    if (offsets instanceof java.util.Map) {
                        Object val = ((java.util.Map) offsets).get(exportProperties.getTopic() + "-" + partitionId);
                        if (val instanceof Number) {
                            return ((Number) val).longValue();
                        }
                    }
                    return 0L;
                })
                .max(Long::compare)
                .orElse(0L);
            
            if (contextOffset > 0) {
                return contextOffset;
            }
            
            Long paramOffset = execution.getJobParameters().getLong("offset-" + partitionId);
            if (paramOffset != null && paramOffset > 0) {
                return paramOffset;
            }
        }
        return 0L;
    }
}

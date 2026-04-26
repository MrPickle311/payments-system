package com.example.payments.export.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.batch.item.kafka.builder.KafkaItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaReaderConfig {

    private final KafkaProperties kafkaProperties;

    @Value("${export.topic}")
    private String topic;

    @Bean
    @StepScope
    public KafkaItemReader<String, String> kafkaItemReader(
            @Value("#{stepExecutionContext['partitionId'] ?: 0}") Integer partitionId) {
        log.info("Creating KafkaItemReader for partitionId: {}", partitionId);
        Properties props = new Properties();
        props.putAll(kafkaProperties.buildConsumerProperties(null)); // Need to pass null for default in SB3

        // Each partition gets its own isolated consumer group.
        // This prevents the "UNKNOWN_MEMBER_ID" error caused by one worker's
        // join/leave triggering a rebalance that invalidates all other workers in
        // the shared group.
        props.put("group.id", "export-batch-group-part-" + partitionId);

        // Fix for CommitFailedException during slow batch processing
        props.put("max.poll.interval.ms", "600000"); // 10 minutes
        props.put("session.timeout.ms", "60000");
        props.put("heartbeat.interval.ms", "15000");
        props.put("max.poll.records", "3");

        return new KafkaItemReaderBuilder<String, String>()
                .partitions(partitionId)
                .consumerProperties(props)
                .name("payments-kafka-reader-" + partitionId)
                .saveState(true) // Crucial for offset management in JobRepository
                .topic(topic)
                .pollTimeout(java.time.Duration.ofSeconds(1)) // Stop waiting for new messages after 1s
                .build();
    }
}

package com.example.payments.export.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.batch.item.kafka.builder.KafkaItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.Properties;
import java.util.HashMap;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaReaderConfig {

  public static final String PARTITION_ID = "partitionId";
  public static final String PARTITION_NAME_PREFIX = "payments-kafka-reader-";
  @SuppressWarnings("rawtypes")
  private final ConsumerFactory consumerFactory;
  private final ExportProperties exportProperties;

  @Bean
  @StepScope
  public KafkaItemReader<String, String> kafkaItemReader() {
    Integer partitionId = 0;
    if (StepSynchronizationManager.getContext() != null) {
      partitionId = getPartitionId();
    }
    Properties props = buildConsumerProperties(partitionId);
    return new KafkaItemReaderBuilder<String, String>().partitions(partitionId)
        .partitionOffsets(new HashMap<>()).consumerProperties(props)
        .name(PARTITION_NAME_PREFIX + partitionId).saveState(true)
        .topic(exportProperties.getTopic()).pollTimeout(Duration.ofSeconds(1)).build();
  }

  private static int getPartitionId() {
    return StepSynchronizationManager.getContext().getStepExecution().getExecutionContext()
        .getInt(PARTITION_ID, 0);
  }

  private Properties buildConsumerProperties(Integer partitionId) {
    Properties props = new Properties();
    props.putAll(consumerFactory.getConfigurationProperties());
    props.put(GROUP_ID_CONFIG, "export-batch-group-part-" + partitionId);
    props.put(AUTO_OFFSET_RESET_CONFIG, exportProperties.getKafka().getAutoOffsetReset());
    props.put(MAX_POLL_INTERVAL_MS_CONFIG, exportProperties.getKafka().getMaxPollIntervalMs());
    props.put(SESSION_TIMEOUT_MS_CONFIG, exportProperties.getKafka().getSessionTimeoutMs());
    props.put(HEARTBEAT_INTERVAL_MS_CONFIG, exportProperties.getKafka().getHeartbeatIntervalMs());
    props.put(MAX_POLL_RECORDS_CONFIG, exportProperties.getKafka().getMaxPollRecords());
    return props;
  }

  @Bean
  public NewTopic paymentLedgerEventsTopic() {
    return TopicBuilder.name(exportProperties.getTopic()).partitions(exportProperties.getGridSize())
        .build();
  }
}

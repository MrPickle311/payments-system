package com.example.payments.export.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.batch.item.kafka.builder.KafkaItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaReaderConfig {

  @SuppressWarnings("rawtypes")
  private final ConsumerFactory consumerFactory;
  private final ExportProperties exportProperties;

  @Bean
  @StepScope
  public KafkaItemReader<String, String> kafkaItemReader(
      @Value("#{stepExecutionContext['partitionId'] ?: 0}") Integer partitionId) {
    log.info("Creating KafkaItemReader for partitionId: {}", partitionId);
    Properties props = new Properties();
    props.putAll(consumerFactory.getConfigurationProperties());
    props.put("group.id", "export-batch-group-part-" + partitionId);
    props.put("max.poll.interval.ms", "600000");
    props.put("session.timeout.ms", "60000");
    props.put("heartbeat.interval.ms", "15000");
    props.put("max.poll.records", "3");
    return new KafkaItemReaderBuilder<String, String>().partitions(partitionId)
        .consumerProperties(props).name("payments-kafka-reader-" + partitionId).saveState(true)
        .topic(exportProperties.getTopic()).pollTimeout(java.time.Duration.ofSeconds(1)).build();
  }

  @Bean
  public org.apache.kafka.clients.admin.NewTopic paymentLedgerEventsTopic() {
    return org.springframework.kafka.config.TopicBuilder.name(exportProperties.getTopic())
        .partitions(exportProperties.getGridSize()).build();
  }
}

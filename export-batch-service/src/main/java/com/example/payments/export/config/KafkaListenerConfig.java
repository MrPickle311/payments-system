package com.example.payments.export.config;

import static org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Profile("listener")
@RequiredArgsConstructor
public class KafkaListenerConfig {

    public static final String DLT = ".DLT";

    private final ExportProperties exportProperties;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {

        // Malformed messages routed to DLT — never silently dropped
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(exportProperties.getGridSize()); // 1 thread per Kafka partition
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(MANUAL);
        return factory;
    }

    @Bean
    public NewTopic paymentLedgerEventsTopic() {
        return TopicBuilder.name(exportProperties.getTopic())
                .partitions(exportProperties.getGridSize())
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(exportProperties.getTopic() + DLT)
                .partitions(1)
                .build();
    }
}

package com.example.payment.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final long MAX_INTERVAL_MS = 30_000L;

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, 2.0);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        // Allow up to 5 minutes of retries before moving to DLT
        backOff.setMaxElapsedTime(300_000L);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}

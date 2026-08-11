package com.example.payment.infrastructure.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalConfig {

    @Bean
    public ExecutorService virtualThreadExecutor() {
        return ContextExecutorService.wrap(
                Executors.newVirtualThreadPerTaskExecutor(), ContextSnapshotFactory.builder().build());
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

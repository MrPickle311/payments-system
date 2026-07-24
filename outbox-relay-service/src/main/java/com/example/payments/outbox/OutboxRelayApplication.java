package com.example.payments.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.example.payments.outbox", "com.example.payments.common"})
@EnableJpaRepositories(basePackages = {"com.example.payments.outbox", "com.example.payments.common"})
public class OutboxRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxRelayApplication.class, args);
    }
}

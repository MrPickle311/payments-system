package com.example.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.example.payment", "com.example.payments.common"})
@EnableScheduling
@EnableRetry
@EnableCaching
@EnableJpaRepositories({
    "com.example.payment.infrastructure.persistence",
    "com.example.payments.common.sharedkernel.outbox"
})
@EntityScan(
        basePackages = {
            "com.example.payment.infrastructure.persistence",
            "com.example.payments.common.sharedkernel.outbox"
        })
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }
}

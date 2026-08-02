package com.example.payments.export.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public MutableClock clock() {
        return new MutableClock();
    }
}

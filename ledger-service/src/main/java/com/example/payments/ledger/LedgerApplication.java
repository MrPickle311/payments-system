package com.example.payments.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.payments.ledger.infrastructure.persistence")
public class LedgerApplication {
  public static void main(String[] args) {
    SpringApplication.run(LedgerApplication.class, args);
  }
}

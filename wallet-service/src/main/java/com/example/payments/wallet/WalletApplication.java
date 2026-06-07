package com.example.payments.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.payments.wallet.infrastructure.persistence")
public class WalletApplication {
  public static void main(String[] args) {
    SpringApplication.run(WalletApplication.class, args);
  }
}

package com.example.payments.export;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExportBatchApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExportBatchApplication.class, args);
  }
}

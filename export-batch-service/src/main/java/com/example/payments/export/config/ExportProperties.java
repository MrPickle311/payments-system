package com.example.payments.export.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "export")
public class ExportProperties {
  private String topic = "payment-ledger-events";
  private int gridSize = 3;
  private int batchSize = 500;
  private Regulatory regulatory = new Regulatory();

  @Data
  public static class Regulatory {
    private String url = "http://localhost:8084/api/v1/regulatory/report";
  }
}

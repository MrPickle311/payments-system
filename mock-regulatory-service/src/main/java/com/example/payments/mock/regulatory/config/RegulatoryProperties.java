package com.example.payments.mock.regulatory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "regulatory")
@Getter
@Setter
public class RegulatoryProperties {
  private double failureRate = 0.2;
}

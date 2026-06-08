package com.example.payments.payment.infrastructure.external.wallet;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wallet.service")
public class WalletProperties {
  private String url;
}

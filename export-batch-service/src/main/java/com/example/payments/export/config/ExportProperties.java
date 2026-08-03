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
    private int maxRetryCount = 3;
    private int lookbackMonths = 3;

    private RegulatoryProperties regulatory = new RegulatoryProperties();
    private ListenerProperties listener = new ListenerProperties();
    private RetryProperties retry = new RetryProperties();

    @Data
    public static class ListenerProperties {
        private String autoOffsetReset = "earliest";
        private String maxPollIntervalMs = "600000";
        private String sessionTimeoutMs = "60000";
        private String heartbeatIntervalMs = "15000";
        private int maxPollRecords = 100;
    }

    @Data
    public static class RegulatoryProperties {
        private String url = "http://localhost:8084/api/v1/regulatory/report";
    }

    @Data
    public static class RetryProperties {
        private String schedule = "0/30 * * * * *";
    }
}

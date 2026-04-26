package com.example.payments.export;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.writer.RegulatoryApiWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@ActiveProfiles("test")
class ExportBatchApplicationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job exportLedgerJob;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void fullBatchFlowTest() throws Exception {
        // 1. Prepare dummy data
        LedgerEvent event = LedgerEvent.builder()
                .paymentId(1L)
                .grossAmount(new BigDecimal("100.00"))
                .netAmount(new BigDecimal("97.00"))
                .currency("USD")
                .timestamp(LocalDateTime.now())
                .build();
        
        String json = objectMapper.writeValueAsString(event);

        // 2. Send to Kafka
        kafkaTemplate.send("payment-ledger-events", json);

        // 3. Launch job
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        
        jobLauncher.run(exportLedgerJob, params);

        // 4. Verify that RestTemplate was called by RegulatoryApiWriter
        verify(restTemplate, timeout(5000).atLeastOnce())
                .postForLocation(eq("http://localhost:8084/api/v1/regulatory/report"), any());
    }
}

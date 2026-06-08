package com.example.payments.payment.infrastructure.external.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerPublisherTest {

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  @InjectMocks
  private LedgerPublisher ledgerPublisher;

  @Test
  void publishEventSuccess() {
    ledgerPublisher.publishEvent(1L, new BigDecimal("100.00"), new BigDecimal("98.00"), "USD");
    verify(kafkaTemplate).send(eq("payment-ledger-events"), eq("1"), anyString());
  }

  @Test
  void publishEventFailure() {
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("Kafka down"));
    ledgerPublisher.publishEvent(1L, new BigDecimal("100.00"), new BigDecimal("98.00"), "USD");
    verify(kafkaTemplate).send(eq("payment-ledger-events"), eq("1"), anyString());
  }
}

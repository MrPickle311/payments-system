package com.example.payments.payment.infrastructure.external.ledger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class LedgerPublisherTest {

  private static final String TOPIC = "payment-ledger-events";
  private static final String KEY = "1";
  private static final String AMOUNT = "100.00";
  private static final String NET_AMOUNT = "98.00";
  private static final String CURRENCY = "USD";

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  @InjectMocks
  private LedgerPublisher ledgerPublisher;

  @Test
  void publishEventSuccess() {
    ledgerPublisher.publishEvent(1L, new BigDecimal(AMOUNT), new BigDecimal(NET_AMOUNT), CURRENCY);
    verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
  }

  @Test
  void publishEventFailure() {
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("Kafka down"));
    ledgerPublisher.publishEvent(1L, new BigDecimal(AMOUNT), new BigDecimal(NET_AMOUNT), CURRENCY);
    verify(kafkaTemplate).send(eq(TOPIC), eq(KEY), anyString());
  }
}

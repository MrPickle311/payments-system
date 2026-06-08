package com.example.payments.payment.infrastructure.external.ledger;

import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public void publishEvent(Long paymentId, BigDecimal gross, BigDecimal net, String currency) {
    log.info("[LedgerPublisher] Publishing ledger event for paymentId={}", paymentId);
    String event = String.format(
        "{\"paymentId\":%d,\"grossAmount\":%s,\"netAmount\":%s,\"currency\":\"%s\",\"timestamp\":\"%s\"}",
        paymentId, gross, net, currency, LocalDateTime.now());
    try {
      kafkaTemplate.send("payment-ledger-events", String.valueOf(paymentId), event);
    } catch (Exception e) {
      log.error("[LedgerPublisher] Failed to send Kafka message: {}", e.getMessage());
    }
  }
}

package com.example.payment.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public void sendWebhook(Long paymentId, String status) {
    log.debug("[WebhookService] Publishing {} webhook for paymentId={} to Kafka", status,
        paymentId);
    String payload = toJsonPayload(paymentId, status);
    kafkaTemplate.send("payment-webhooks", String.valueOf(paymentId), payload);
  }

    private static String toJsonPayload(Long paymentId, String status) {
        return String.format("{\"paymentId\":%d,\"status\":\"%s\"}", paymentId, status);
    }
}

package com.example.payments.webhooks.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebhookKafkaListener {

  @KafkaListener(topics = "payment-webhooks", groupId = "webhooks-group")
  public void consumeWebhookEvent(String payload) {
    log.info("[WebhookService] Received webhook event from Kafka: {}", payload);
    log.info("[WebhookService] Successfully pushed webhook to external system.");
  }
}

package com.example.payments.payment.application.saga.external;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WebhookService {
  public void sendWebhook(Long paymentId, String status) {
    log.info("Sending webhook for payment {} with status {}", paymentId, status);
  }
}

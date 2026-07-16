package com.example.payments.webhooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
public class WebhooksApplication {
  public static final String TOPIC_PAYMENT_EVENTS = "payment-events";
  public static final String PAYMENT_ID_KEY = "paymentId";

  public static void main(String[] args) {
    SpringApplication.run(WebhooksApplication.class, args);
  }

  @Bean
  public CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxAttempts(5);
    return new DefaultErrorHandler(recoverer, backOff);
  }
}

@Entity
@Table(name = "webhook_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class WebhookConfig {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long merchantId;
  private String url;
  private String secretSignature;
  private boolean isActive;
}

@Entity
@Table(name = "webhook_delivery_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class WebhookDeliveryLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long merchantId;
  private Long paymentId;
  private String eventType;
  private int retryCount;
  private Integer httpStatusCode;
  private String errorMessage;
  private boolean isDelivered;
}

@Repository
interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {
  Optional<WebhookConfig> findByMerchantId(Long merchantId);
}

@Repository
interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
}

@Component
@Slf4j
class MockAnalyst {

  private static final String MANUAL_REVIEW_EVENT = "PaymentManualReviewEvent";
  private final SecureRandom random = new SecureRandom();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${payment.service.url:http://payment-service:8080}")
  private String paymentServiceUrl;

  @KafkaListener(topics = WebhooksApplication.TOPIC_PAYMENT_EVENTS, groupId = "mock-analyst-group")
  public void handleManualReview(String message, @Header("type") String type) {
    if (!MANUAL_REVIEW_EVENT.equals(type)) {
      return;
    }
    try {
      Map<String, Object> map = objectMapper.readValue(message, Map.class);
      Number pIdNum = (Number) map.get(WebhooksApplication.PAYMENT_ID_KEY);
      processReview(pIdNum != null ? pIdNum.longValue() : null);
    } catch (Exception e) {
      log.error("[MockAnalyst] Failed: {}", e.getMessage());
    }
  }

  private void processReview(Long paymentId) {
    if (paymentId == null) {
      return;
    }
    try {
      Thread.sleep(500);
      String ev = random.nextDouble() < 0.95 ? "ANALYST_APPROVE" : "ANALYST_REJECT";
      log.info("[MockAnalyst] Decision for paymentId={}: {}", paymentId, ev);
      sendDecision(paymentId, ev);
    } catch (Exception e) {
      log.error("[MockAnalyst] Failed decision: {}", e.getMessage());
    }
  }

  private void sendDecision(Long paymentId, String event) {
    RestClient.create().post().uri(paymentServiceUrl + "/api/webhook/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(WebhooksApplication.PAYMENT_ID_KEY, paymentId, "event", event))
        .retrieve().toBodilessEntity();
  }
}

@Component
@Slf4j
class WebhookDispatcher {

  private static final String EVENT_COMPLETED = "PaymentCompletedEvent";
  private final WebhookConfigRepository configRepo;
  private final WebhookDeliveryLogRepository logRepo;
  private final ObjectMapper objectMapper;

  public WebhookDispatcher(WebhookConfigRepository configRepo,
                           WebhookDeliveryLogRepository logRepo,
                           ObjectMapper objectMapper) {
    this.configRepo = configRepo;
    this.logRepo = logRepo;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = WebhooksApplication.TOPIC_PAYMENT_EVENTS, groupId = "webhook-service-group")
  public void handlePaymentCompleted(String msg, @Header("type") String type) {
    if (!EVENT_COMPLETED.equals(type)) {
      return;
    }
    try {
      Map<String, Object> map = objectMapper.readValue(msg, Map.class);
      Number pId = (Number) map.get(WebhooksApplication.PAYMENT_ID_KEY);
      if (pId != null) {
        dispatchWebhook(pId.longValue(), type);
      }
    } catch (Exception e) {
      log.error("[Webhook] Parse failed: {}", e.getMessage());
    }
  }

  private void dispatchWebhook(Long paymentId, String eventType) {
    Long merchantId = 1L;
    WebhookConfig config = configRepo.findByMerchantId(merchantId)
        .orElseGet(() -> createDefaultConfig(merchantId));
    WebhookDeliveryLog deliveryLog = WebhookDeliveryLog.builder()
        .merchantId(merchantId).paymentId(paymentId)
        .eventType(eventType).retryCount(0).isDelivered(false).build();
    sendWithRetry(config, deliveryLog);
  }

  private WebhookConfig createDefaultConfig(Long merchantId) {
    WebhookConfig config = WebhookConfig.builder()
        .merchantId(merchantId).url("http://localhost:8089/webhook")
        .secretSignature("mock_secret_key_123").isActive(true).build();
    return configRepo.save(config);
  }

  private void sendWithRetry(WebhookConfig config, WebhookDeliveryLog logEntry) {
    long delay = 1000L;
    for (int attempt = 1; attempt <= 5; attempt++) {
      logEntry.setRetryCount(attempt - 1);
      if (trySend(config, logEntry)) {
        return;
      }
      delay = backoffDelay(delay, attempt, logEntry);
    }
  }

  private boolean trySend(WebhookConfig config, WebhookDeliveryLog logEntry) {
    try {
      executePost(config, logEntry);
      updateLogSuccess(logEntry);
      return true;
    } catch (Exception e) {
      updateLogFailure(logEntry, e.getMessage());
      return false;
    }
  }

  private void executePost(WebhookConfig config, WebhookDeliveryLog logEntry) {
    RestClient.create().post().uri(config.getUrl())
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Signature", config.getSecretSignature())
        .body(logEntry).retrieve().toBodilessEntity();
  }

  private void updateLogSuccess(WebhookDeliveryLog logEntry) {
    logEntry.setDelivered(true);
    logEntry.setHttpStatusCode(200);
    logEntry.setErrorMessage(null);
    logRepo.save(logEntry);
  }

  private void updateLogFailure(WebhookDeliveryLog logEntry, String msg) {
    logEntry.setHttpStatusCode(500);
    logEntry.setErrorMessage(msg);
  }

  private long backoffDelay(long currentDelay, int attempt, WebhookDeliveryLog logEntry) {
    if (attempt == 5) {
      logRepo.save(logEntry);
      log.error("[Webhook] Delivery failed for paymentId={}", logEntry.getPaymentId());
    } else {
      sleep(currentDelay);
    }
    return currentDelay * 2;
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

@Entity
@Table(name = "system_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SystemAuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long paymentId;
  private String eventType;
  private String payload;
  private java.time.LocalDateTime createdAt;
}

@Repository
interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, Long> {
}

@Component
@Slf4j
class SystemAuditLogConsumer {
  private final SystemAuditLogRepository logRepository;
  private final ObjectMapper objectMapper;

  public SystemAuditLogConsumer(SystemAuditLogRepository logRepository, ObjectMapper objectMapper) {
    this.logRepository = logRepository;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = WebhooksApplication.TOPIC_PAYMENT_EVENTS, groupId = "audit-log-group")
  public void handleEvent(String msg, @Header("type") String type) {
    try {
      Map<String, Object> map = objectMapper.readValue(msg, Map.class);
      Number pId = (Number) map.get(WebhooksApplication.PAYMENT_ID_KEY);
      SystemAuditLog logEntry = SystemAuditLog.builder()
          .paymentId(pId != null ? pId.longValue() : null)
          .eventType(type)
          .payload(msg)
          .createdAt(java.time.LocalDateTime.now())
          .build();
      logRepository.save(logEntry);
      log.info("[AuditLog] Saved audit log for event type: {}", type);
    } catch (Exception e) {
      log.error("[AuditLog] Failed to save audit log", e);
    }
  }
}

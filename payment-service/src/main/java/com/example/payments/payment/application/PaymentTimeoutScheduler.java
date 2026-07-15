package com.example.payments.payment.application;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PaymentTimeoutScheduler {

  private final PaymentRepository paymentRepository;
  private final PaymentService paymentService;
  private final long timeoutSeconds;

  public PaymentTimeoutScheduler(PaymentRepository paymentRepository, PaymentService paymentService,
      @Value("${payment.saga.manual-review-timeout-seconds:60}") long timeoutSeconds) {
    this.paymentRepository = paymentRepository;
    this.paymentService = paymentService;
    this.timeoutSeconds = timeoutSeconds;
  }

  @Scheduled(fixedDelayString = "${payment.saga.timeout-check-interval-ms:5000}")
  public void checkPendingTimeouts() {
    LocalDateTime threshold = LocalDateTime.now(ZoneId.systemDefault()).minusSeconds(timeoutSeconds);
    List<Payment> pending = paymentRepository.findByState(PaymentState.MANUAL_REVIEW.name());
    pending.forEach(payment -> processTimeoutIfExpired(payment, threshold));
  }

  private void processTimeoutIfExpired(Payment payment, LocalDateTime threshold) {
    if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(threshold)) {
      log.warn("[Scheduler] Manual review timed out for payment={}. Auto-rejecting.",
          payment.getId());
      try {
        paymentService.processEvent(payment.getId(), PaymentEvent.ANALYST_REJECT);
      } catch (Exception e) {
        log.error("[Scheduler] Error rejecting timed out payment={}", payment.getId(), e);
      }
    }
  }
}

package com.example.payments.payment.application.saga.external;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LimitsService {
  public boolean checkAndReserveLimits(Long paymentId) {
    log.info("Simulating limits check for payment {}", paymentId);
    try {
      Thread.sleep(150);
    } catch (Exception e) {
    }
    return true;
  }

  public void releaseLimit(Long paymentId) {
    log.info("Releasing limit for payment {}", paymentId);
  }
}

package com.example.payments.payment.application.saga.external;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthorizationService {
  public boolean authorize(Long paymentId) {
    log.info("Simulating authorization for payment {}", paymentId);
    try {
      Thread.sleep(200);
    } catch (Exception e) {
    }
    return true;
  }

  public void reverseAuthorization(Long paymentId) {
    log.info("Reversing authorization for payment {}", paymentId);
  }
}

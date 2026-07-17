package com.example.payments.payment.application.saga.external;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SanctionsService {
  public boolean checkSanctions(Long paymentId) {
    log.info("Simulating sanctions check for payment {}", paymentId);
    try {
      Thread.sleep(300);
    } catch (Exception e) {
    }
    return true;
  }
}

package com.example.payments.payment.application.saga.external;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FxService {
  public boolean processFx(Long paymentId) {
    log.info("Simulating FX processing for payment {}", paymentId);
    return true;
  }
}

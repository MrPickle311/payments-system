package com.example.payment.domain.event;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public interface PaymentDomainEvent {
  Long getPaymentId();
}

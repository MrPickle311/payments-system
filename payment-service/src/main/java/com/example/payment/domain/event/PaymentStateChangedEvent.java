package com.example.payment.domain.event;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record PaymentStateChangedEvent(Long paymentId, String oldState, String newState) implements PaymentDomainEvent {
    @Override
    public Long getPaymentId() {
        return paymentId;
    }
}

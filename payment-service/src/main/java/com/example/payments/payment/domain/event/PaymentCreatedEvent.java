package com.example.payments.payment.domain.event;

import com.example.payments.payment.domain.Money;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record PaymentCreatedEvent(Long paymentId, String transactionId, Money money) implements PaymentDomainEvent {
    @Override
    public Long getPaymentId() {
        return paymentId;
    }
}

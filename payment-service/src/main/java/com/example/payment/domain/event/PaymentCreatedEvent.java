package com.example.payment.domain.event;

import com.example.payments.common.sharedkernel.Money;
import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record PaymentCreatedEvent(Long paymentId, String transactionId, Money money) implements PaymentDomainEvent {
    @Override
    public Long getPaymentId() {
        return paymentId;
    }
}

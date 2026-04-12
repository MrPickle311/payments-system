package com.example.payments.payment.domain;

import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.payment.domain.event.PaymentDomainEvent;
import com.example.payments.common.domain.Money;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core payment aggregate root.
 *
 * Implements a pure domain model, completely decoupled from JPA/Spring Data.
 * Uses jMolecules annotations for DDD tactical patterns.
 */
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.jmolecules.ddd.annotation.AggregateRoot
public class Payment {

    private Long id;
    private String transactionId;
    private Money money;
    @Builder.Default
    private String state = PaymentState.NEW.name();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private final List<PaymentDomainEvent> domainEvents = new ArrayList<>();

    /** Convenience accessor that parses the stored string back to the enum. */
    public PaymentState currentState() {
        if (this.state == null) return null;
        String rootStateName = this.state.split(",")[0];
        return PaymentState.valueOf(rootStateName);
    }
    
    public void publishStateChange(PaymentState oldState, PaymentState newState) {
        if (oldState != newState) {
            registerEvent(new com.example.payments.payment.domain.event.PaymentStateChangedEvent(id, oldState.name(), newState.name()));
        }
    }

    public void registerCreationEvent() {
        registerEvent(new com.example.payments.payment.domain.event.PaymentCreatedEvent(id, transactionId, money));
    }

    protected void registerEvent(PaymentDomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<PaymentDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}

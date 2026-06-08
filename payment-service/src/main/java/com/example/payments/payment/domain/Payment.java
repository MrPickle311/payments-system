package com.example.payments.payment.domain;

import com.example.payments.payment.domain.event.PaymentCreatedEvent;
import com.example.payments.payment.domain.event.PaymentStateChangedEvent;

import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.domain.event.PaymentDomainEvent;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.ToString;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.jmolecules.ddd.annotation.AggregateRoot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@AggregateRoot
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


  public PaymentState currentState() {
    if (this.state == null)
      return null;
    String rootStateName = this.state.split(",")[0];
    return PaymentState.valueOf(rootStateName);
  }

  public void publishStateChange(PaymentState oldState, PaymentState newState) {
    if (oldState != newState) {
      registerEvent(new PaymentStateChangedEvent(id, oldState.name(), newState.name()));
    }
  }

  public void registerCreationEvent() {
    registerEvent(new PaymentCreatedEvent(id, transactionId, money));
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

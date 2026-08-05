package com.example.payment.domain;

import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FAILED;
import static com.example.payment.domain.enums.PaymentState.NEW;
import static com.example.payment.domain.enums.PaymentState.valueOf;

import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.event.PaymentCreatedEvent;
import com.example.payment.domain.event.PaymentDomainEvent;
import com.example.payment.domain.event.PaymentStateChangedEvent;
import com.example.payments.common.sharedkernel.Money;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jmolecules.ddd.annotation.AggregateRoot;

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
    private String state = NEW.name();

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private Long version;

    private Long sourceUserId;
    private Long targetUserId;
    private String sourceCurrency;
    private String targetCurrency;

    private Integer fraudScore;
    private String fraudRisk;
    private BigDecimal processingFee;
    private BigDecimal netAmount;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private final List<PaymentDomainEvent> domainEvents = new ArrayList<>();

    public PaymentState currentState() {
        if (this.state == null) {
            return null;
        }
        String rootStateName = this.state.split(",")[0];
        return valueOf(rootStateName);
    }

    public boolean isTerminal() {
        PaymentState current = currentState();
        return current == COMPLETED || current == FAILED;
    }

    public void markFraudEvaluation(Integer score, String risk) {
        this.fraudScore = score;
        this.fraudRisk = risk;
    }

    public void updateFinancialDetails(BigDecimal fee, BigDecimal net) {
        this.processingFee = fee;
        this.netAmount = net;
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

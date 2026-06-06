package com.example.payments.payment.application;

import com.example.payments.common.domain.enums.PaymentEvent;
import com.example.payments.common.domain.enums.PaymentState;
import com.example.payments.payment.domain.Payment;
import lombok.Builder;
import lombok.Getter;
import org.springframework.statemachine.StateMachine;

import java.util.Collection;

@Builder
@Getter
public class GuardCheckContext {
    private final StateMachine<PaymentState, PaymentEvent> stateMachine;
    private final Payment payment;
    private final PaymentEvent event;
    private final Collection<PaymentState> stateBeforeIds;
    private final Collection<PaymentState> stateAfterIds;
}

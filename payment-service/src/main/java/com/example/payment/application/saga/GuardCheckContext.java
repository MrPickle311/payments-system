package com.example.payment.application.saga;

import com.example.payment.domain.Payment;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.util.Collection;
import lombok.Builder;
import lombok.Getter;
import org.springframework.statemachine.StateMachine;

@Builder
@Getter
public class GuardCheckContext {
    private final StateMachine<PaymentState, PaymentEvent> stateMachine;
    private final Payment payment;
    private final PaymentEvent event;
    private final Collection<PaymentState> stateBeforeIds;
    private final Collection<PaymentState> stateAfterIds;
}

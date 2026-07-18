package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.Payment;
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

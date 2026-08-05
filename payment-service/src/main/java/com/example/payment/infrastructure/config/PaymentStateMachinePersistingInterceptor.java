package com.example.payment.infrastructure.config;

import com.example.payment.application.saga.SagaContextProxy;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentStateMachinePersistingInterceptor
        extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

    private final PaymentRepository paymentRepository;
    private final PaymentStateMachinePersister persister;

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50))
    public void postStateChange(
            State<PaymentState, PaymentEvent> state,
            Message<PaymentEvent> message,
            Transition<PaymentState, PaymentEvent> transition,
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

        if (stateMachine != rootStateMachine) {
            return;
        }

        Long paymentId = SagaContextProxy.of(rootStateMachine).getPaymentId();
        if (paymentId != null) {
            savePaymentState(rootStateMachine, paymentId);
        }
    }

    private void savePaymentState(StateMachine<PaymentState, PaymentEvent> rootStateMachine, Long paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            persister.persist(rootStateMachine, payment);
            Payment saved = paymentRepository.save(payment);
            log.info("[PersistingInterceptor] Saved state {} for payment {}", saved.getState(), paymentId);
        });
    }
}

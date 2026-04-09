package com.example.payments.interceptor;

import com.example.payments.entity.PaymentHistory;
import com.example.payments.repository.PaymentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import com.example.payments.enums.PaymentEvent;
import com.example.payments.enums.PaymentState;
import org.springframework.messaging.Message;

/**
 * Audit interceptor that writes an immutable {@link PaymentHistory} record
 * after every successful state transition.
 *
 * <p>This interceptor is registered on each state machine instance in
 * {@link com.example.payments.service.PaymentService} before the event is
 * sent.  Because the service method is {@code @Transactional}, the
 * {@code PaymentHistory} insert participates in the same transaction as the
 * {@code Payment} state update — both commit or both roll back together,
 * guaranteeing consistency between the live state and the audit trail.
 *
 * <p>The interceptor reads the {@code paymentId} from the machine's extended
 * state, which the persister populates when it restores the machine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStateMachineInterceptor
        extends StateMachineInterceptorAdapter<PaymentState, PaymentEvent> {

    private final PaymentHistoryRepository paymentHistoryRepository;

    /**
     * Called by the state machine framework after the target state has been
     * entered and the transition is fully committed.
     *
     * <p>Internal (self) transitions — e.g. PENDING → PENDING on REDIRECT —
     * are also intercepted, giving a complete record of every event processed.
     */
    @Override
    public void postStateChange(
            State<PaymentState, PaymentEvent> state,
            Message<PaymentEvent> message,
            Transition<PaymentState, PaymentEvent> transition,
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            StateMachine<PaymentState, PaymentEvent> rootStateMachine) {

        if (transition == null) {
            // Guard: can happen on machine start; no real transition to record.
            return;
        }

        Long paymentId = stateMachine.getExtendedState()
                .get("paymentId", Long.class);

        if (paymentId == null) {
            log.warn("[Interceptor] postStateChange fired but paymentId is not set "
                    + "in extended state — skipping history record.");
            return;
        }

        String fromState = transition.getSource() != null
                ? transition.getSource().getId().name()
                : "UNKNOWN";
        String toState = transition.getTarget() != null
                ? transition.getTarget().getId().name()
                : "UNKNOWN";
        String eventName = (message != null && message.getPayload() != null)
                ? message.getPayload().name()
                : "INTERNAL";

        PaymentHistory record = PaymentHistory.builder()
                .paymentId(paymentId)
                .fromState(fromState)
                .toState(toState)
                .event(eventName)
                .build();

        paymentHistoryRepository.save(record);

        log.info("[Audit] payment={} | {} ──{}──► {}",
                paymentId, fromState, eventName, toState);
    }
}

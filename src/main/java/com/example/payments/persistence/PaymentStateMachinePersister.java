package com.example.payments.persistence;

import com.example.payments.entity.Payment;
import com.example.payments.enums.PaymentEvent;
import com.example.payments.enums.PaymentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Component;

/**
 * Custom {@link StateMachinePersister} that maps between a {@link Payment}
 * entity (the canonical state store) and the in-memory state machine.
 *
 * <h2>Why a custom persister instead of the Spring State Machine JPA module?</h2>
 * The Spring State Machine JPA integration ({@code spring-statemachine-data-jpa})
 * maintains its own set of tables ({@code STATE_MACHINE_CONTEXT}, etc.) and
 * serialises the entire machine context.  For domain-focused applications it is
 * simpler and more transparent to store only the current state name in the
 * business entity itself and reconstruct the machine context on the fly.  This
 * gives us:
 * <ul>
 *   <li>A single source of truth for payment state (the {@code payments} table).</li>
 *   <li>No hidden serialisation formats that break on enum renames.</li>
 *   <li>Full control over what ends up in extended state (e.g. {@code paymentId}).</li>
 * </ul>
 *
 * <h2>Restore lifecycle and the isRestoring flag</h2>
 * {@code StateMachine.start()} triggers entry actions for the current state.
 * If a payment is already COMPLETED and we restore the machine, the
 * {@code completedEntryAction} would fire again (sending a duplicate invoice,
 * unlocking the product twice, etc.).  Setting {@code isRestoring = true}
 * <em>before</em> calling {@code start()} lets the action detect this scenario
 * and skip processing.  The flag is cleared immediately after start.
 */
@Slf4j
@Component
public class PaymentStateMachinePersister
        implements StateMachinePersister<PaymentState, PaymentEvent, Payment> {

    /**
     * Writes the machine's current state back into the Payment entity.
     *
     * <p>The caller is responsible for saving the entity to the database afterwards
     * (typically via {@code paymentRepository.save(payment)} at the end of the
     * service transaction).
     */
    @Override
    public void persist(StateMachine<PaymentState, PaymentEvent> stateMachine,
                        Payment payment) {
        PaymentState newState = stateMachine.getState().getId();
        log.debug("[Persister] Persisting state {} for payment {}", newState, payment.getId());
        payment.setState(newState.name());
    }

    /**
     * Restores the state machine to the state recorded in the Payment entity
     * without triggering any side-effecting entry actions.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Stop the machine (required before {@code resetStateMachine}).</li>
     *   <li>Build a {@link DefaultStateMachineContext} from the stored state.</li>
     *   <li>Inject {@code paymentId} and {@code isRestoring=true} into
     *       {@link org.springframework.statemachine.ExtendedState} so actions
     *       and interceptors have access to the domain ID.</li>
     *   <li>Reset the machine to the restored context.</li>
     *   <li>Start the machine (fires entry actions, but they respect isRestoring).</li>
     *   <li>Clear the isRestoring flag so subsequent real transitions work normally.</li>
     * </ol>
     */
    @Override
    public StateMachine<PaymentState, PaymentEvent> restore(
            StateMachine<PaymentState, PaymentEvent> stateMachine,
            Payment payment) {

        PaymentState storedState = payment.currentState();
        log.debug("[Persister] Restoring state machine to state {} for payment {}",
                storedState, payment.getId());

        // Build extended state carrying domain context for actions, guards & interceptors.
        // Guards and actions should read payment data from here rather than querying
        // the DB themselves — it keeps them stateless and avoids extra round-trips.
        DefaultExtendedState extendedState = new DefaultExtendedState();
        extendedState.getVariables().put("paymentId",       payment.getId());
        extendedState.getVariables().put("paymentAmount",   payment.getAmount());
        extendedState.getVariables().put("paymentCurrency", payment.getCurrency());
        extendedState.getVariables().put("paymentCreatedAt", payment.getCreatedAt());
        extendedState.getVariables().put("isRestoring",     Boolean.TRUE);

        DefaultStateMachineContext<PaymentState, PaymentEvent> context =
                new DefaultStateMachineContext<>(storedState, null, null, extendedState);

        stateMachine.stop();

        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(context));

        stateMachine.start();

        // Clear the restore flag so real transitions process actions normally.
        stateMachine.getExtendedState().getVariables().put("isRestoring", Boolean.FALSE);

        return stateMachine;
    }
}

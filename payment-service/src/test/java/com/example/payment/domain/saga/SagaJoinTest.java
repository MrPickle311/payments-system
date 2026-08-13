package com.example.payment.domain.saga;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_FAIL;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_SUCCESS;
import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_FAILED;
import static com.example.payment.domain.enums.PaymentState.AUTH_PENDING;
import static com.example.payment.domain.enums.PaymentState.AUTH_REJECTED;
import static com.example.payment.domain.enums.PaymentState.COMPENSATING;
import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FEE_CALCULATED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.FEE_COMPENSATION_SKIPPED;
import static com.example.payment.domain.enums.PaymentState.FEE_EVALUATING;
import static com.example.payment.domain.enums.PaymentState.FRAUD_DETECTED;
import static com.example.payment.domain.enums.PaymentState.FRAUD_PASSED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFICATION_FAILED;
import static com.example.payment.domain.enums.PaymentState.LEDGER_NOTIFIED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_COMPENSATED;
import static com.example.payment.domain.enums.PaymentState.LIMITS_OK;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT_FAILED;
import static com.example.payment.domain.enums.PaymentState.WALLET_SETTLEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.domain.enums.PaymentState;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The join decision, exercised without a state machine or a database. */
class SagaJoinTest {

    /** All five PROCESSING regions finished successfully. */
    private static Set<PaymentState> allProcessingRegionsPassed() {
        return EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);
    }

    @Nested
    @DisplayName("PROCESSING")
    class Processing {

        @Test
        @DisplayName("emits COMPLETE once every region has passed")
        void completesWhenAllRegionsPass() {
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(allProcessingRegionsPassed()))
                    .contains(COMPLETE);
        }

        @Test
        @DisplayName("emits FAIL when any region failed")
        void failsWhenAnyRegionFails() {
            Set<PaymentState> states =
                    EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_DETECTED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(states)).contains(FAIL);
        }

        @Test
        @DisplayName("does not fire while a region is still in progress")
        void doesNotFireEarly() {
            Set<PaymentState> fourDoneOneRunning =
                    EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_EVALUATING);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(fourDoneOneRunning)).isEmpty();
        }

        @Test
        @DisplayName("counts distinct regions, not terminal states")
        void requiresDistinctRegionsNotJustCount() {
            // Five terminal states, but they come from only four regions - FeeCheck contributes
            // FEE_CALCULATED and FEE_CHARGED, while SanctionsCheck has not finished at all.
            // The previous implementation counted ids and fired at >= 5, joining a saga whose
            // sanctions check was still running. This matters most for history-based recovery,
            // where a payment's whole transition history is replayed into one set.
            Set<PaymentState> fiveStatesFourRegions =
                    EnumSet.of(PROCESSING, AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, FEE_CALCULATED, FEE_CHARGED);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(fiveStatesFourRegions)).isEmpty();
        }

        @Test
        @DisplayName("treats a rejected authorization as a failure")
        void authRejectionFails() {
            Set<PaymentState> states =
                    EnumSet.of(PROCESSING, AUTH_REJECTED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(states)).contains(FAIL);
        }

        @Test
        @DisplayName("treats a technical authorization failure as a failure")
        void authTechnicalFailureFails() {
            Set<PaymentState> states =
                    EnumSet.of(PROCESSING, AUTH_FAILED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(states)).contains(FAIL);
        }
    }

    @Nested
    @DisplayName("SETTLEMENT")
    class Settlement {

        @Test
        @DisplayName("a notified ledger settles the payment")
        void ledgerNotifiedSucceeds() {
            assertThat(SagaJoin.SETTLEMENT_JOIN.evaluate(EnumSet.of(SETTLEMENT, LEDGER_NOTIFIED)))
                    .contains(SETTLEMENT_SUCCESS);
        }

        @Test
        @DisplayName("a FAILED ledger notification still settles the payment - the money already moved")
        void ledgerNotificationFailureStillSucceeds() {
            // Deliberate: LEDGER_NOTIFICATION_FAILED is only reachable from WALLET_SETTLED, i.e.
            // after the funds moved. The notification is an audit record published via the outbox
            // and retried independently, so failing settlement here would send an already-settled
            // payment to COMPENSATING and reverse real money over an audit write.
            assertThat(SagaJoin.SETTLEMENT_JOIN.evaluate(EnumSet.of(SETTLEMENT, LEDGER_NOTIFICATION_FAILED)))
                    .contains(SETTLEMENT_SUCCESS);
        }

        @Test
        @DisplayName("a failed wallet settlement fails the payment")
        void settlementFailureFails() {
            assertThat(SagaJoin.SETTLEMENT_JOIN.evaluate(EnumSet.of(SETTLEMENT, SETTLEMENT_FAILED)))
                    .contains(SETTLEMENT_FAIL);
        }

        @Test
        @DisplayName("does not fire mid-settlement")
        void doesNotFireEarly() {
            assertThat(SagaJoin.SETTLEMENT_JOIN.evaluate(EnumSet.of(SETTLEMENT, WALLET_SETTLEMENT)))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("COMPENSATING")
    class Compensating {

        @Test
        @DisplayName("fails the payment once both compensations settle")
        void failsWhenBothRegionsDone() {
            assertThat(SagaJoin.COMPENSATING_JOIN.evaluate(
                            EnumSet.of(COMPENSATING, FEE_COMPENSATED, LIMITS_COMPENSATED)))
                    .contains(FAIL);
        }

        @Test
        @DisplayName("skipped compensations still count as finished")
        void skippedCompensationsCount() {
            assertThat(SagaJoin.COMPENSATING_JOIN.evaluate(
                            EnumSet.of(COMPENSATING, FEE_COMPENSATION_SKIPPED, LIMITS_COMPENSATED)))
                    .contains(FAIL);
        }

        @Test
        @DisplayName("does not fire while one compensation is outstanding")
        void doesNotFireEarly() {
            assertThat(SagaJoin.COMPENSATING_JOIN.evaluate(EnumSet.of(COMPENSATING, FEE_COMPENSATED)))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("decide")
    class Decide {

        @Test
        @DisplayName("returns nothing when no composite is active")
        void noCompositeNoJoin() {
            assertThat(SagaJoin.decide(EnumSet.of(COMPLETED))).isEmpty();
            assertThat(SagaJoin.decide(EnumSet.noneOf(PaymentState.class))).isEmpty();
            assertThat(SagaJoin.decide(null)).isEmpty();
        }

        @Test
        @DisplayName("selects the join for the composite that is actually active")
        void selectsActiveComposite() {
            assertThat(SagaJoin.decide(allProcessingRegionsPassed()))
                    .map(SagaJoin.JoinDecision::join)
                    .contains(SagaJoin.PROCESSING_JOIN);

            assertThat(SagaJoin.decide(EnumSet.of(SETTLEMENT, LEDGER_NOTIFIED)))
                    .map(SagaJoin.JoinDecision::join)
                    .contains(SagaJoin.SETTLEMENT_JOIN);
        }
    }

    @Nested
    @DisplayName("evaluateRegions")
    class EvaluateRegions {

        @Test
        @DisplayName("ignores the composite marker, for reconciling from transition history")
        void worksWithoutTheCompositePresent() {
            // History replays region states without a reliable composite marker; the caller already
            // knows which composite the payment is in from its persisted root state.
            Set<PaymentState> regionStatesOnly =
                    EnumSet.of(AUTH_APPROVED, FRAUD_PASSED, LIMITS_OK, SANCTIONS_CLEARED, FEE_CHARGED);

            assertThat(SagaJoin.PROCESSING_JOIN.evaluate(regionStatesOnly)).isEmpty();
            assertThat(SagaJoin.PROCESSING_JOIN.evaluateRegions(regionStatesOnly))
                    .contains(COMPLETE);
        }

        @Test
        @DisplayName("tolerates the in-progress states history accumulates alongside terminals")
        void toleratesHistoricalInProgressStates() {
            // Real history contains every state ever entered, e.g. AUTH_PENDING before AUTH_APPROVED.
            Set<PaymentState> history = EnumSet.of(
                    AUTH_PENDING,
                    AUTH_APPROVED,
                    FRAUD_PASSED,
                    LIMITS_OK,
                    SANCTIONS_CLEARED,
                    FEE_EVALUATING,
                    FEE_CALCULATED,
                    FEE_CHARGED);
            assertThat(SagaJoin.PROCESSING_JOIN.evaluateRegions(history)).contains(COMPLETE);
        }
    }

    @Test
    @DisplayName("join keys keep their legacy values so in-flight machines are unaffected")
    void joinKeysAreStable() {
        assertThat(SagaJoin.PROCESSING_JOIN.joinKey()).isEqualTo("SAGA_PROCESSING_JOIN_TRIGGERED");
        assertThat(SagaJoin.SETTLEMENT_JOIN.joinKey()).isEqualTo("SAGA_SETTLEMENT_JOIN_TRIGGERED");
        assertThat(SagaJoin.COMPENSATING_JOIN.joinKey()).isEqualTo("SAGA_COMPENSATING_JOIN_TRIGGERED");
    }

    @Test
    @DisplayName("each composite has exactly one join, so a composite cannot join twice")
    void joinKeysAreUniquePerComposite() {
        assertThat(java.util.Arrays.stream(SagaJoin.values())
                        .map(SagaJoin::joinKey)
                        .distinct()
                        .count())
                .isEqualTo(SagaJoin.values().length);
    }
}

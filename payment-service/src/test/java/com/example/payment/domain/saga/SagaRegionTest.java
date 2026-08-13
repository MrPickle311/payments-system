package com.example.payment.domain.saga;

import static com.example.payment.domain.enums.PaymentState.AUTH_APPROVED;
import static com.example.payment.domain.enums.PaymentState.AUTH_FAILED;
import static com.example.payment.domain.enums.PaymentState.COMPLETED;
import static com.example.payment.domain.enums.PaymentState.FEE_CHARGED;
import static com.example.payment.domain.enums.PaymentState.FX_CONVERSION;
import static com.example.payment.domain.enums.PaymentState.LIMITS_FAILED;
import static com.example.payment.domain.enums.PaymentState.NEW;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SANCTIONS_FAILED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SagaRegionTest {

    @ParameterizedTest(name = "{0} belongs to region {1}")
    @CsvSource({
        // These three are the regression: the previous hardcoded switch in PaymentHistoryInterceptor
        // omitted them, so a region that failed *technically* was recorded against region "ROOT".
        // Anything reconciling finished regions from payment_history then undercounted and would
        // never recognise the join as ready.
        "AUTH_FAILED, Authorization",
        "LIMITS_FAILED, LimitsCheck",
        "SANCTIONS_FAILED, SanctionsCheck",
        // Previously-covered cases, kept so the fix cannot regress the states that did work.
        "AUTH_PENDING, Authorization",
        "AUTH_APPROVED, Authorization",
        "FRAUD_DETECTED, FraudCheck",
        "LIMITS_OK, LimitsCheck",
        "SANCTIONS_CLEARED, SanctionsCheck",
        "FEE_CHARGED, FeeCheck",
        "FEE_CALCULATED, FeeCheck",
        // Compensation and settlement regions were never labelled at all before.
        "FEE_COMPENSATED, FeeCompensation",
        "LIMITS_COMPENSATED, LimitsCompensation",
        "LEDGER_NOTIFIED, Settlement",
        "SETTLEMENT_FAILED, Settlement",
    })
    void labelsStatesWithTheirOwningRegion(String state, String expectedLabel) {
        assertThat(SagaRegion.labelOf(com.example.payment.domain.enums.PaymentState.valueOf(state)))
                .isEqualTo(expectedLabel);
    }

    @ParameterizedTest(name = "{0} is a root-level state")
    @CsvSource({"NEW", "PROCESSING", "SETTLEMENT", "COMPENSATING", "COMPLETED", "FAILED", "FX_CONVERSION"})
    void labelsRootStatesAsRoot(String state) {
        assertThat(SagaRegion.labelOf(com.example.payment.domain.enums.PaymentState.valueOf(state)))
                .isEqualTo(SagaRegion.ROOT_REGION);
    }

    @Test
    @DisplayName("PROCESSING has the five parallel regions the .puml declares")
    void processingHasFiveRegions() {
        assertThat(SagaRegion.forComposite(PROCESSING))
                .extracting(SagaRegion::getLabel)
                .containsExactlyInAnyOrder("Authorization", "FraudCheck", "LimitsCheck", "SanctionsCheck", "FeeCheck");
    }

    @Test
    @DisplayName("composites without regions report none")
    void compositesWithoutRegions() {
        assertThat(SagaRegion.forComposite(FX_CONVERSION)).isEmpty();
        assertThat(SagaRegion.forComposite(COMPLETED)).isEmpty();
        assertThat(SagaRegion.forComposite(NEW)).isEmpty();
    }

    @Test
    @DisplayName("terminality is reported per region, not globally")
    void terminalityIsScopedToTheRegion() {
        SagaRegion authorization = SagaRegion.AUTHORIZATION;

        assertThat(authorization.isTerminal(AUTH_APPROVED)).isTrue();
        assertThat(authorization.isTerminal(AUTH_FAILED)).isTrue();
        assertThat(authorization.isFailureTerminal(AUTH_FAILED)).isTrue();
        assertThat(authorization.isFailureTerminal(AUTH_APPROVED)).isFalse();

        // A terminal state of a *different* region must not satisfy this one.
        assertThat(authorization.isTerminal(FEE_CHARGED)).isFalse();
    }

    @Test
    @DisplayName("LIMITS_FAILED and SANCTIONS_FAILED do not fail the join - preserved pre-existing behaviour")
    void technicalFailuresInLimitsAndSanctionsDoNotFailTheJoin() {
        // Documents a real inconsistency rather than asserting it is correct: AUTH_FAILED and
        // FRAUD_FAILED fail the join, but LIMITS_FAILED and SANCTIONS_FAILED do not, so a payment
        // whose limits or sanctions check errored technically still completes. This mirrors the
        // behaviour that was already live before the join was refactored; changing it changes
        // payment outcomes and needs a product decision, so it is pinned here to make any future
        // change deliberate and visible.
        assertThat(SagaRegion.LIMITS_CHECK.isFailureTerminal(LIMITS_FAILED)).isFalse();
        assertThat(SagaRegion.LIMITS_CHECK.isTerminal(LIMITS_FAILED)).isTrue();

        assertThat(SagaRegion.SANCTIONS_CHECK.isFailureTerminal(SANCTIONS_FAILED))
                .isFalse();
        assertThat(SagaRegion.SANCTIONS_CHECK.isTerminal(SANCTIONS_FAILED)).isTrue();

        assertThat(SagaRegion.AUTHORIZATION.isFailureTerminal(AUTH_FAILED)).isTrue();
    }
}

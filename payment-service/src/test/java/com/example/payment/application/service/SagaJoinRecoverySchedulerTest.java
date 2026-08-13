package com.example.payment.application.service;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.saga.SagaJoin;
import com.example.payment.domain.saga.SagaPendingEvent;
import com.example.payment.domain.saga.SagaPendingEventRepository;
import com.example.payment.infrastructure.persistence.PaymentJpaEntity;
import com.example.payment.infrastructure.persistence.SpringDataPaymentHistoryRepository;
import com.example.payment.infrastructure.persistence.SpringDataPaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * One test per recovery layer, each simulating the crash window that layer exists to cover.
 *
 * <p>The composite string used throughout — {@code PROCESSING,AUTH_APPROVED,...} — is the real
 * format {@code PaymentStateMachinePersister} writes: root state first, then one state per active
 * region.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaJoinRecoverySchedulerTest {

    private static final Long PAYMENT = 9001L;
    private static final String JOIN_KEY = "SAGA_PROCESSING_JOIN_TRIGGERED";

    private static final String ALL_REGIONS_PASSED =
            "PROCESSING,AUTH_APPROVED,FRAUD_PASSED,LIMITS_OK,SANCTIONS_CLEARED,FEE_CHARGED";
    private static final String STILL_RUNNING =
            "PROCESSING,AUTH_APPROVED,FRAUD_PASSED,LIMITS_OK,SANCTIONS_CLEARED,FEE_EVALUATING";

    @Mock
    private SagaPendingEventRepository pendingEventRepository;

    @Mock
    private SpringDataPaymentRepository paymentRepository;

    @Mock
    private SpringDataPaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private PaymentService paymentService;

    private SagaJoinRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC);
        scheduler = new SagaJoinRecoveryScheduler(
                pendingEventRepository, paymentRepository, paymentHistoryRepository, paymentService, fixed);

        // Default happy path: this pod wins whatever it contends for. Tests in "multi-pod
        // contention" override these to prove a losing pod backs off instead of double-redriving.
        when(pendingEventRepository.tryLease(any(), any(), any())).thenReturn(true);
        when(pendingEventRepository.claim(any(), any(), any())).thenReturn(true);
    }

    private static PaymentJpaEntity paymentInState(String compositeState) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setId(PAYMENT);
        entity.setState(compositeState);
        return entity;
    }

    private static SagaPendingEvent pending(PaymentEvent event) {
        return new SagaPendingEvent(
                1L,
                PAYMENT,
                event,
                JOIN_KEY,
                SagaPendingEvent.Status.PENDING,
                1,
                null,
                OffsetDateTime.parse("2026-08-13T11:00:00Z"),
                null);
    }

    @Nested
    @DisplayName("layer 1 - the join was claimed but never confirmed delivered")
    class PendingDispatches {

        @Test
        @DisplayName("re-drives the claimed event and confirms it")
        void redrivesAndConfirms() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of(pending(COMPLETE)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenReturn(true);

            scheduler.recoverPendingDispatches();

            verify(paymentService).redriveJoin(PAYMENT, COMPLETE);
            verify(pendingEventRepository).markDispatched(PAYMENT, JOIN_KEY);
        }

        @Test
        @DisplayName("leaves the claim outstanding when the machine still refuses the event")
        void keepsClaimWhenRefused() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of(pending(COMPLETE)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenReturn(false);

            scheduler.recoverPendingDispatches();

            verify(pendingEventRepository, never()).markDispatched(anyLong(), anyString());
        }

        @Test
        @DisplayName("records the error and moves on when a re-drive throws")
        void oneFailureDoesNotStopTheBatch() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of(pending(COMPLETE)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenThrow(new IllegalStateException("boom"));

            scheduler.recoverPendingDispatches();

            verify(pendingEventRepository).markFailed(eq(PAYMENT), eq(JOIN_KEY), anyString());
        }

        @Test
        @DisplayName("does nothing when no dispatch is outstanding")
        void noopWhenNothingPending() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of());

            scheduler.recoverPendingDispatches();

            verifyNoInteractions(paymentService);
        }
    }

    @Nested
    @DisplayName("layer 2 - the persisted composite already shows the join is ready")
    class PersistedStateReconciliation {

        @Test
        @DisplayName("re-drives a payment whose regions have all finished")
        void redrivesJoinableState() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(ALL_REGIONS_PASSED)));

            scheduler.reconcileJoinablePayments();

            verify(paymentService).redriveJoin(PAYMENT, COMPLETE);
            // Layer 2 resolved it, so history is never consulted.
            verifyNoInteractions(paymentHistoryRepository);
        }

        @Test
        @DisplayName("leaves a genuinely in-flight payment alone")
        void ignoresPaymentsStillWorking() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(STILL_RUNNING)));
            when(paymentHistoryRepository.findDistinctToStatesByPaymentId(PAYMENT))
                    .thenReturn(List.of());

            scheduler.reconcileJoinablePayments();

            verify(paymentService, never()).redriveJoin(anyLong(), any());
        }

        @Test
        @DisplayName("fails the payment when a region failed")
        void redrivesFailWhenARegionFailed() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(
                            "PROCESSING,AUTH_APPROVED,FRAUD_DETECTED,LIMITS_OK,SANCTIONS_CLEARED,FEE_CHARGED")));

            scheduler.reconcileJoinablePayments();

            verify(paymentService).redriveJoin(PAYMENT, FAIL);
        }
    }

    @Nested
    @DisplayName("layer 3 - the claim transaction never committed, so the composite is stale")
    class HistoryReconciliation {

        @Test
        @DisplayName("recovers using transition history when the persisted state looks unfinished")
        void redrivesFromHistoryWhenStateIsStale() {
            // The crash landed before the co-transactional claim+persist committed: payments.state
            // still shows FeeCheck running, but payment_history proves it reached FEE_CHARGED,
            // because region transitions are recorded independently as each region finishes.
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(STILL_RUNNING)));
            when(paymentHistoryRepository.findDistinctToStatesByPaymentId(PAYMENT))
                    .thenReturn(List.of(
                            "AUTH_PENDING",
                            "AUTH_APPROVED",
                            "FRAUD_PASSED",
                            "LIMITS_OK",
                            "SANCTIONS_CLEARED",
                            "FEE_EVALUATING",
                            "FEE_CALCULATED",
                            "FEE_CHARGED"));

            scheduler.reconcileJoinablePayments();

            verify(paymentService).redriveJoin(PAYMENT, COMPLETE);
        }

        @Test
        @DisplayName("does not re-drive when history also shows the payment unfinished")
        void doesNotRedriveWhenHistoryAgreesItIsUnfinished() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(STILL_RUNNING)));
            when(paymentHistoryRepository.findDistinctToStatesByPaymentId(PAYMENT))
                    .thenReturn(List.of("AUTH_APPROVED", "FRAUD_PASSED", "LIMITS_OK", "FEE_EVALUATING"));

            scheduler.reconcileJoinablePayments();

            verify(paymentService, never()).redriveJoin(anyLong(), any());
        }

        @Test
        @DisplayName("picks the join for the composite the payment is actually in, not one from its past")
        void usesRootStateRatherThanAnyCompositeSeenInHistory() {
            // History accumulates PROCESSING *and* SETTLEMENT once a payment moves on. Choosing the
            // join by scanning history for any composite would pick whichever came first in
            // declaration order and re-drive the wrong phase.
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState("SETTLEMENT,WALLET_SETTLED")));
            when(paymentHistoryRepository.findDistinctToStatesByPaymentId(PAYMENT))
                    .thenReturn(List.of(
                            "PROCESSING",
                            "AUTH_APPROVED",
                            "FRAUD_PASSED",
                            "LIMITS_OK",
                            "SANCTIONS_CLEARED",
                            "FEE_CHARGED",
                            "SETTLEMENT",
                            "WALLET_SETTLEMENT",
                            "WALLET_SETTLED",
                            "LEDGER_NOTIFIED"));

            scheduler.reconcileJoinablePayments();

            verify(paymentService).redriveJoin(PAYMENT, PaymentEvent.SETTLEMENT_SUCCESS);
            verify(paymentService, never()).redriveJoin(PAYMENT, COMPLETE);
        }
    }

    @Nested
    @DisplayName("multi-pod contention")
    class MultiPodContention {

        @Test
        @DisplayName("layer 1: a pod that loses the lease race does not re-drive")
        void layerOneBacksOffWhenLeaseLost() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of(pending(COMPLETE)));
            // Another pod's sweep already leased this row between our SELECT and this UPDATE.
            when(pendingEventRepository.tryLease(any(), any(), any())).thenReturn(false);

            scheduler.recoverPendingDispatches();

            verifyNoInteractions(paymentService);
            verify(pendingEventRepository, never()).markDispatched(anyLong(), anyString());
        }

        @Test
        @DisplayName("layer 1 leases each row for itself before re-driving it")
        void layerOneLeasesBeforeRedriving() {
            when(pendingEventRepository.findStalePending(any(), anyInt())).thenReturn(List.of(pending(COMPLETE)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenReturn(true);

            scheduler.recoverPendingDispatches();

            var order = org.mockito.Mockito.inOrder(pendingEventRepository, paymentService);
            order.verify(pendingEventRepository).tryLease(eq(1L), anyString(), any());
            order.verify(paymentService).redriveJoin(PAYMENT, COMPLETE);
        }

        @Test
        @DisplayName("layers 2/3: a pod that loses the claim race does not re-drive")
        void layerTwoAndThreeBackOffWhenClaimLost() {
            // Two pods both see the same joinable composite in the same sweep - only the one that
            // wins the (payment_id, join_key) insert may act on it.
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(ALL_REGIONS_PASSED)));
            when(pendingEventRepository.claim(PAYMENT, COMPLETE, JOIN_KEY)).thenReturn(false);

            scheduler.reconcileJoinablePayments();

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("layers 2/3 claim the join before re-driving it, using the same guard the live path uses")
        void layerTwoAndThreeClaimBeforeRedriving() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(ALL_REGIONS_PASSED)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenReturn(true);

            scheduler.reconcileJoinablePayments();

            var order = org.mockito.Mockito.inOrder(pendingEventRepository, paymentService);
            order.verify(pendingEventRepository).claim(PAYMENT, COMPLETE, JOIN_KEY);
            order.verify(paymentService).redriveJoin(PAYMENT, COMPLETE);
            order.verify(pendingEventRepository).markDispatched(PAYMENT, JOIN_KEY);
        }

        @Test
        @DisplayName("layers 2/3 record failure when the claimed re-drive is refused")
        void layerTwoAndThreeRecordFailureOnRefusal() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState(ALL_REGIONS_PASSED)));
            when(paymentService.redriveJoin(PAYMENT, COMPLETE)).thenReturn(false);

            scheduler.reconcileJoinablePayments();

            verify(pendingEventRepository).markFailed(eq(PAYMENT), eq(JOIN_KEY), anyString());
        }
    }

    @Nested
    @DisplayName("robustness")
    class Robustness {

        @Test
        @DisplayName("ignores payments with no persisted state")
        void ignoresBlankState() {
            when(paymentRepository.findStuckPayments(any(), anyInt())).thenReturn(List.of(paymentInState(null)));

            scheduler.reconcileJoinablePayments();

            verify(paymentService, never()).redriveJoin(anyLong(), any());
        }

        @Test
        @DisplayName("ignores unrecognised state names rather than failing the whole sweep")
        void toleratesUnknownStateNames() {
            // Guards against an old row written before a state was renamed taking down the sweeper.
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState("PROCESSING,AUTH_APPROVED,SOMETHING_REMOVED")));
            when(paymentHistoryRepository.findDistinctToStatesByPaymentId(PAYMENT))
                    .thenReturn(List.of("GONE_STATE"));

            scheduler.reconcileJoinablePayments();

            verify(paymentService, never()).redriveJoin(anyLong(), any());
        }

        @Test
        @DisplayName("a payment in a composite with no join is left alone")
        void ignoresCompositesWithoutJoins() {
            when(paymentRepository.findStuckPayments(any(), anyInt()))
                    .thenReturn(List.of(paymentInState("FX_CONVERSION")));

            scheduler.reconcileJoinablePayments();

            verify(paymentService, never()).redriveJoin(anyLong(), any());
        }

        @Test
        @DisplayName("SagaJoin is the single decision point shared by the interceptor and every layer")
        void reconciliationUsesTheSamePredicateAsTheInterceptor() {
            // Documents the invariant that keeps detection and recovery from drifting apart.
            var states = java.util.EnumSet.of(
                    com.example.payment.domain.enums.PaymentState.PROCESSING,
                    com.example.payment.domain.enums.PaymentState.AUTH_APPROVED,
                    com.example.payment.domain.enums.PaymentState.FRAUD_PASSED,
                    com.example.payment.domain.enums.PaymentState.LIMITS_OK,
                    com.example.payment.domain.enums.PaymentState.SANCTIONS_CLEARED,
                    com.example.payment.domain.enums.PaymentState.FEE_CHARGED);
            org.assertj.core.api.Assertions.assertThat(SagaJoin.decide(states))
                    .map(SagaJoin.JoinDecision::event)
                    .contains(COMPLETE);
        }
    }
}

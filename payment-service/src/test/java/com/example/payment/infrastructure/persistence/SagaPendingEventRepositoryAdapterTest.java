package com.example.payment.infrastructure.persistence;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.SETTLEMENT_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.saga.SagaPendingEvent;
import com.example.payment.domain.saga.SagaPendingEvent.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the durable join guard against a real database.
 *
 * <p>The guard that replaced the old in-heap {@code putIfAbsent} is nothing but
 * {@code UNIQUE (payment_id, join_key)}. Verifying it with mocks would verify nothing, so this runs
 * against H2 with the schema built from the entity mapping.
 */
@DataJpaTest
@Import({SagaPendingEventRepositoryAdapter.class, SagaPendingEventRepositoryAdapterTest.TestConfig.class})
@TestPropertySource(
        properties = {
            // Schema comes from the entity mapping: the production schema.sql and Liquibase
            // changelog are both Postgres-specific (TIMESTAMPTZ, bigserial) and will not run on H2.
            "spring.liquibase.enabled=false",
            "spring.sql.init.mode=never",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:sagaguard;DB_CLOSE_DELAY=-1",
            // Production sets Postgres session settings (statement_timeout, client_min_messages)
            // that H2 cannot parse.
            "spring.datasource.hikari.connection-init-sql=",
        })
class SagaPendingEventRepositoryAdapterTest {

    /**
     * The application enables caching globally; the JPA slice does not start the cache
     * autoconfiguration, so supply a no-op manager rather than widening the slice.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private static final String PROCESSING_KEY = "SAGA_PROCESSING_JOIN_TRIGGERED";
    private static final String SETTLEMENT_KEY = "SAGA_SETTLEMENT_JOIN_TRIGGERED";

    /**
     * Distinct payment id per test. Two tests here run outside a transaction (they have to, to
     * observe a real constraint violation and real concurrency), so their rows genuinely commit and
     * would otherwise be visible to the rolled-back ones.
     */
    private static final AtomicLong PAYMENT_IDS = new AtomicLong(1000L);

    @Autowired
    private SagaPendingEventRepositoryAdapter adapter;

    @Autowired
    private SpringDataSagaPendingEventRepository repository;

    private ExecutorService executor;
    private Long payment;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        payment = PAYMENT_IDS.incrementAndGet();
    }

    private List<SagaPendingEventJpaEntity> rowsForThisPayment() {
        return repository.findAll().stream()
                .filter(entity -> entity.getPaymentId().equals(payment)
                        || entity.getPaymentId().equals(payment + 1))
                .toList();
    }

    private Long insertPendingRow(Long paymentId, PaymentEvent event, String joinKey) {
        adapter.claim(paymentId, event, joinKey);
        return repository
                .findByPaymentIdAndJoinKey(paymentId, joinKey)
                .orElseThrow()
                .getId();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("the first claim on a join wins")
    void firstClaimWins() {
        assertThat(adapter.claim(payment, COMPLETE, PROCESSING_KEY)).isTrue();
        assertThat(repository.findByPaymentIdAndJoinKey(payment, PROCESSING_KEY))
                .isPresent();
    }

    @Test
    @DisplayName("a second claim on the same join is refused")
    void secondClaimIsRefused() {
        adapter.claim(payment, COMPLETE, PROCESSING_KEY);

        assertThat(adapter.claim(payment, COMPLETE, PROCESSING_KEY)).isFalse();
        assertThat(rowsForThisPayment()).hasSize(1);
    }

    @Test
    @DisplayName("different joins on the same payment are independent")
    void differentJoinsOnSamePaymentCoexist() {
        assertThat(adapter.claim(payment, COMPLETE, PROCESSING_KEY)).isTrue();
        assertThat(adapter.claim(payment, SETTLEMENT_SUCCESS, SETTLEMENT_KEY)).isTrue();

        assertThat(rowsForThisPayment()).hasSize(2);
    }

    @Test
    @DisplayName("the same join on different payments is independent")
    void sameJoinOnDifferentPaymentsCoexists() {
        assertThat(adapter.claim(payment, COMPLETE, PROCESSING_KEY)).isTrue();
        assertThat(adapter.claim(payment + 1, COMPLETE, PROCESSING_KEY)).isTrue();

        assertThat(rowsForThisPayment()).hasSize(2);
    }

    @Test
    @DisplayName("the database rejects a duplicate even when the pre-check is bypassed")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsDuplicateInsert() {
        // The adapter's findByPaymentIdAndJoinKey pre-check is only a fast path. Two pods can both
        // pass it concurrently, so the guarantee has to come from the constraint itself - this
        // inserts straight through the repository to prove the constraint is really there.
        repository.saveAndFlush(SagaPendingEventJpaEntity.builder()
                .paymentId(payment)
                .event(COMPLETE.name())
                .joinKey(PROCESSING_KEY)
                .status(Status.PENDING.name())
                .attempts(1)
                .build());

        assertThatThrownBy(() -> repository.saveAndFlush(SagaPendingEventJpaEntity.builder()
                        .paymentId(payment)
                        .event(COMPLETE.name())
                        .joinKey(PROCESSING_KEY)
                        .status(Status.PENDING.name())
                        .attempts(1)
                        .build()))
                .as("without this constraint two pods would both dispatch the same join")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("only one of two concurrent claimants wins")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentClaimsProduceExactlyOneWinner() throws Exception {
        Callable<Boolean> claim = () -> {
            try {
                return adapter.claim(payment, COMPLETE, PROCESSING_KEY);
            } catch (DataIntegrityViolationException lostRace) {
                return false;
            }
        };

        Future<Boolean> first = executor.submit(claim);
        Future<Boolean> second = executor.submit(claim);

        assertThat(List.of(first.get(), second.get()))
                .as("exactly one caller must own the dispatch")
                .containsExactlyInAnyOrder(true, false);
        assertThat(rowsForThisPayment()).hasSize(1);
    }

    @Test
    @DisplayName("confirming a dispatch marks it terminal")
    void markDispatchedIsTerminal() {
        adapter.claim(payment, COMPLETE, PROCESSING_KEY);

        adapter.markDispatched(payment, PROCESSING_KEY);

        assertThat(repository.findByPaymentIdAndJoinKey(payment, PROCESSING_KEY))
                .get()
                .extracting(SagaPendingEventJpaEntity::getStatus)
                .isEqualTo(Status.DISPATCHED.name());
    }

    @Test
    @DisplayName("a failed dispatch stays PENDING and records why, so the pollers retry it")
    void markFailedKeepsItRetryable() {
        adapter.claim(payment, COMPLETE, PROCESSING_KEY);

        adapter.markFailed(payment, PROCESSING_KEY, "State machine denied event COMPLETE");

        assertThat(repository.findByPaymentIdAndJoinKey(payment, PROCESSING_KEY))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getStatus()).isEqualTo(Status.PENDING.name());
                    assertThat(entity.getAttempts()).isEqualTo(2);
                    assertThat(entity.getLastError()).contains("denied");
                });
    }

    @Test
    @DisplayName("stale PENDING claims are found, confirmed ones are not")
    void findsOnlyStalePendingClaims() {
        adapter.claim(payment, COMPLETE, PROCESSING_KEY);
        adapter.claim(payment + 1, SETTLEMENT_SUCCESS, SETTLEMENT_KEY);
        adapter.markDispatched(payment + 1, SETTLEMENT_KEY);

        // Scoped to this test's payments: the query is global, and the two non-transactional tests
        // above genuinely commit rows that stay visible.
        List<Long> stale = adapter.findStalePending(OffsetDateTime.now().plusMinutes(1), 100).stream()
                .map(SagaPendingEvent::paymentId)
                .filter(id -> id.equals(payment) || id.equals(payment + 1))
                .toList();

        assertThat(stale).as("a confirmed dispatch must never be re-driven").containsExactly(payment);
    }

    @Test
    @DisplayName("claims newer than the grace period are left alone")
    void ignoresClaimsInsideTheGracePeriod() {
        adapter.claim(payment, COMPLETE, PROCESSING_KEY);

        List<Long> stale = adapter.findStalePending(OffsetDateTime.now().minusMinutes(1), 100).stream()
                .map(SagaPendingEvent::paymentId)
                .filter(id -> id.equals(payment))
                .toList();

        assertThat(stale).as("a dispatch still in flight must not be re-driven").isEmpty();
    }

    @Test
    @DisplayName("the first lease on a row wins")
    void firstLeaseWins() {
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);

        assertThat(adapter.tryLease(id, "pod-a", Duration.ofSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("a second pod cannot lease a row while another pod's lease is live")
    void secondLeaseIsRefusedWhileFirstIsLive() {
        // This is what stops layer 1's poller from re-driving the same PENDING row twice when
        // several pods run the same @Scheduled sweep - claim() already granted the row to someone,
        // so a second insert can't arbitrate the retry the way it arbitrates the first attempt.
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);
        adapter.tryLease(id, "pod-a", Duration.ofSeconds(60));

        assertThat(adapter.tryLease(id, "pod-b", Duration.ofSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("a lease can be retaken once it has expired")
    void expiredLeaseCanBeRetaken() {
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);
        // Negative duration: the lease is already in the past the instant it is granted, simulating
        // a pod that took the lease and then died before finishing the re-drive.
        adapter.tryLease(id, "pod-a", Duration.ofSeconds(-1));

        assertThat(adapter.tryLease(id, "pod-b", Duration.ofSeconds(60)))
                .as("a dead pod's lease must not block recovery forever")
                .isTrue();
    }

    @Test
    @DisplayName("markDispatched releases the lease")
    void markDispatchedReleasesLease() {
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);
        adapter.tryLease(id, "pod-a", Duration.ofSeconds(60));

        adapter.markDispatched(payment, PROCESSING_KEY);

        assertThat(repository.findByPaymentIdAndJoinKey(payment, PROCESSING_KEY))
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getLockedBy()).isNull();
                    assertThat(entity.getLockedUntil()).isNull();
                });
    }

    @Test
    @DisplayName("markFailed releases the lease so the row is immediately retakeable")
    void markFailedReleasesLease() {
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);
        adapter.tryLease(id, "pod-a", Duration.ofSeconds(60));

        adapter.markFailed(payment, PROCESSING_KEY, "boom");

        assertThat(adapter.tryLease(id, "pod-b", Duration.ofSeconds(60)))
                .as("a failed attempt already happened - nothing left to protect against")
                .isTrue();
    }

    @Test
    @DisplayName("only one of two concurrent lease attempts wins")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLeaseAttemptsProduceExactlyOneWinner() throws Exception {
        Long id = insertPendingRow(payment, COMPLETE, PROCESSING_KEY);

        Callable<Boolean> lease =
                () -> adapter.tryLease(id, Thread.currentThread().getName(), Duration.ofSeconds(60));
        Future<Boolean> first = executor.submit(lease);
        Future<Boolean> second = executor.submit(lease);

        assertThat(List.of(first.get(), second.get()))
                .as("of several pods polling the same stale row, exactly one may re-drive it")
                .containsExactlyInAnyOrder(true, false);
    }
}

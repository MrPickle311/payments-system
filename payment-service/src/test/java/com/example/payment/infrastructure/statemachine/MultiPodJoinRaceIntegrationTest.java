package com.example.payment.infrastructure.statemachine;

import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.application.saga.ParallelSagaJoinInterceptor;
import com.example.payment.application.saga.ProcessingStateMachineTestSupport;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaPendingEvent;
import com.example.payment.infrastructure.config.PaymentStateMachinePersister;
import com.example.payment.infrastructure.persistence.SagaPendingEventRepositoryAdapter;
import com.example.payment.infrastructure.persistence.SpringDataSagaPendingEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.statemachine.StateMachine;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two independently-built, independently-completing {@link StateMachine} instances — standing in
 * for two pods that both restored the same payment — race to dispatch the same join through the
 * real {@link SagaJoinClaimService} against a real H2-backed {@code saga_pending_events} table.
 * Confirms exactly one wins, using the production classes, not mocks of them.
 */
@DataJpaTest
@Import({
    SagaPendingEventRepositoryAdapter.class,
    PaymentStateMachinePersister.class,
    SagaJoinClaimService.class,
    PersistentSagaJoinDispatcher.class,
    MultiPodJoinRaceIntegrationTest.TestConfig.class
})
@TestPropertySource(
        properties = {
            "spring.liquibase.enabled=false",
            "spring.sql.init.mode=never",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.datasource.url=jdbc:h2:mem:multipodjoin;DB_CLOSE_DELAY=-1",
            "spring.datasource.hikari.connection-init-sql=",
        })
class MultiPodJoinRaceIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ExecutorService virtualThreadExecutor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        @Bean
        PaymentRepository paymentRepository() {
            return new InMemoryPaymentRepository();
        }
    }

    /** Minimal in-memory stand-in; the point of this test is the DB-backed join guard, not payment CRUD. */
    static class InMemoryPaymentRepository implements PaymentRepository {
        private final Map<Long, Payment> store = new ConcurrentHashMap<>();

        void seed(Payment payment) {
            store.put(payment.getId(), payment);
        }

        @Override
        public Optional<Payment> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Payment> findByIdWithLock(Long id) {
            return findById(id);
        }

        @Override
        public Optional<Payment> findByTransactionId(String transactionId) {
            return Optional.empty();
        }

        @Override
        public Payment save(Payment payment) {
            store.put(payment.getId(), payment);
            return payment;
        }

        @Override
        public boolean existsById(Long id) {
            return store.containsKey(id);
        }
    }

    private static final Long PAYMENT_ID = 777L;
    private static final String JOIN_KEY = "SAGA_PROCESSING_JOIN_TRIGGERED";

    @Autowired
    private PersistentSagaJoinDispatcher dispatcher;

    @Autowired
    private SpringDataSagaPendingEventRepository pendingEventRepository;

    @Autowired
    private InMemoryPaymentRepository paymentRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void exactlyOnePodDeliversTheJoin() throws Exception {
        paymentRepository.seed(Payment.builder()
                .id(PAYMENT_ID)
                .transactionId("tx-" + PAYMENT_ID)
                .state(PROCESSING.name())
                .build());

        StateMachine<PaymentState, PaymentEvent> podA = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        StateMachine<PaymentState, PaymentEvent> podB = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        attachRealDispatcher(podA);
        attachRealDispatcher(podB);

        // Both pods' machines finish PROCESSING back to back, so the real dispatch each triggers
        // races on virtual threads against the shared DB rather than running sequentially.
        ProcessingStateMachineTestSupport.completeAuthFraudLimitsSanctions(podA, PAYMENT_ID);
        ProcessingStateMachineTestSupport.completeAuthFraudLimitsSanctions(podB, PAYMENT_ID);
        ProcessingStateMachineTestSupport.completeFeeCheck(podA, PAYMENT_ID);
        ProcessingStateMachineTestSupport.completeFeeCheck(podB, PAYMENT_ID);

        SagaPendingEvent settled = awaitTerminalRow(PAYMENT_ID, JOIN_KEY, Duration.ofSeconds(5));

        assertThat(settled.status()).isEqualTo(SagaPendingEvent.Status.DISPATCHED);
        assertThat(pendingEventRepository.findAll())
                .as("only one claim row for this payment+join - the guard the whole design rests on")
                .filteredOn(e ->
                        e.getPaymentId().equals(PAYMENT_ID) && e.getJoinKey().equals(JOIN_KEY))
                .hasSize(1);

        boolean aSettled = podA.getState().getId() == SETTLEMENT;
        boolean bSettled = podB.getState().getId() == SETTLEMENT;
        assertThat(aSettled ^ bSettled)
                .as("exactly one machine's sendEvent was accepted")
                .isTrue();
    }

    private void attachRealDispatcher(StateMachine<PaymentState, PaymentEvent> sm) {
        sm.getStateMachineAccessor()
                .doWithAllRegions(
                        accessor -> accessor.addStateMachineInterceptor(new ParallelSagaJoinInterceptor(dispatcher)));
    }

    private SagaPendingEvent awaitTerminalRow(Long paymentId, String joinKey, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            var row = pendingEventRepository.findByPaymentIdAndJoinKey(paymentId, joinKey);
            if (row.isPresent() && !"PENDING".equals(row.get().getStatus())) {
                return new SagaPendingEvent(
                        row.get().getId(),
                        row.get().getPaymentId(),
                        PaymentEvent.valueOf(row.get().getEvent()),
                        row.get().getJoinKey(),
                        SagaPendingEvent.Status.valueOf(row.get().getStatus()),
                        row.get().getAttempts(),
                        row.get().getLastError(),
                        row.get().getCreatedAt(),
                        row.get().getUpdatedAt());
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for join to settle for payment " + paymentId);
    }
}

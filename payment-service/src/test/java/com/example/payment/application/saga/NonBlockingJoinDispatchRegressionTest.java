package com.example.payment.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Pins why {@code PersistentSagaJoinDispatcher} hops onto a virtual thread: making dispatch
 * synchronous would throw {@code IllegalStateException} on Reactor's non-blocking {@code parallel}
 * threads, and the framework swallows that with only a {@code log.warn} — the join is lost silently.
 *
 * <p>Simulated with bare Reactor/JDK types so it can't depend on Spring State Machine's internals
 * shifting; {@code ParallelJoinStateMachineTest} proves the same thing against the real library.
 */
class NonBlockingJoinDispatchRegressionTest {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        virtualThreadExecutor.shutdownNow();
    }

    /**
     * {@code Mono.just(x).block()} would NOT reproduce the failure — {@code MonoJust} short-circuits
     * {@code block()}, skipping the non-blocking-thread check. {@code reduce} makes it non-scalar,
     * matching what {@code AbstractStateMachine.sendEvent(Message)} actually does.
     */
    private static boolean sendEventThatBlocksInternally() {
        return Boolean.TRUE.equals(
                Flux.just(Boolean.TRUE).reduce(Boolean.FALSE, (a, b) -> a || b).block());
    }

    @Test
    @DisplayName("Reactor's parallel scheduler threads are marked non-blocking")
    void parallelSchedulerThreadsAreNonBlocking() throws Exception {
        AtomicBoolean nonBlocking = new AtomicBoolean();

        Mono.fromRunnable(() -> nonBlocking.set(Schedulers.isInNonBlockingThread()))
                .subscribeOn(Schedulers.parallel())
                .then()
                .toFuture()
                .get();

        assertThat(nonBlocking)
                .as("postStateChange runs on these threads; if they stop being non-blocking the "
                        + "premise behind the dispatcher's thread hop no longer holds")
                .isTrue();
    }

    @Test
    @DisplayName("dispatching inline on a parallel-scheduler thread throws")
    void inlineDispatchOnAReactorThreadIsRejected() {
        assertThatThrownBy(() -> Mono.fromCallable(NonBlockingJoinDispatchRegressionTest::sendEventThatBlocksInternally)
                        .subscribeOn(Schedulers.parallel())
                        .toFuture()
                        .get())
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("block()");
    }

    @Test
    @DisplayName("hopping to a virtual thread makes the identical call legal")
    void dispatchOnAVirtualThreadSucceeds() {
        CompletableFuture<Boolean> dispatched = CompletableFuture.supplyAsync(
                NonBlockingJoinDispatchRegressionTest::sendEventThatBlocksInternally, virtualThreadExecutor);

        assertThatCode(dispatched::get).doesNotThrowAnyException();
        assertThat(dispatched).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("virtual threads are not marked non-blocking, which is what makes the hop work")
    void virtualThreadsPermitBlocking() throws Exception {
        CompletableFuture<Boolean> nonBlocking =
                CompletableFuture.supplyAsync(Schedulers::isInNonBlockingThread, virtualThreadExecutor);

        assertThat(nonBlocking.get()).isFalse();
    }
}

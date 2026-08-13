package com.example.payment.application.saga;

import static com.example.payment.domain.enums.PaymentEvent.COMPLETE;
import static com.example.payment.domain.enums.PaymentEvent.FAIL;
import static com.example.payment.domain.enums.PaymentState.PROCESSING;
import static com.example.payment.domain.enums.PaymentState.SETTLEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import com.example.payment.domain.saga.SagaJoin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import reactor.core.scheduler.Schedulers;

/**
 * Drives a real, running {@link StateMachine} through all 5 {@code PROCESSING} regions — no mocks,
 * no Spring context — to prove the join fires against the actual framework, not a simulation of it.
 */
class ParallelJoinStateMachineTest {

    private static final Long PAYMENT_ID = 42L;

    /** Captures each dispatch call and the thread it happened on. */
    private static final class RecordingDispatcher implements SagaJoinDispatcher {
        final List<SagaJoin.JoinDecision> decisions = new ArrayList<>();
        String threadName;
        boolean nonBlockingThread;

        @Override
        public void dispatch(
                StateMachine<PaymentState, PaymentEvent> rootStateMachine,
                Long paymentId,
                SagaJoin join,
                PaymentEvent event) {
            threadName = Thread.currentThread().getName();
            nonBlockingThread = Schedulers.isInNonBlockingThread();
            decisions.add(new SagaJoin.JoinDecision(join, event));
        }
    }

    @Test
    @DisplayName("joins with COMPLETE once all 5 regions reach a success terminal")
    void allRegionsSucceed() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        StateMachine<PaymentState, PaymentEvent> sm = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        sm.getStateMachineAccessor()
                .doWithAllRegions(
                        accessor -> accessor.addStateMachineInterceptor(new ParallelSagaJoinInterceptor(dispatcher)));

        ProcessingStateMachineTestSupport.completeAuthFraudLimitsSanctions(sm, PAYMENT_ID);
        assertThat(dispatcher.decisions)
                .as("4 of 5 regions done - not joinable yet")
                .isEmpty();

        ProcessingStateMachineTestSupport.completeFeeCheck(sm, PAYMENT_ID);

        assertThat(dispatcher.decisions).containsExactly(new SagaJoin.JoinDecision(SagaJoin.PROCESSING_JOIN, COMPLETE));
    }

    @Test
    @DisplayName("joins with FAIL once all regions finish and one of them failed")
    void oneRegionFails() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        StateMachine<PaymentState, PaymentEvent> sm = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        sm.getStateMachineAccessor()
                .doWithAllRegions(
                        accessor -> accessor.addStateMachineInterceptor(new ParallelSagaJoinInterceptor(dispatcher)));

        ProcessingStateMachineTestSupport.send(sm, PAYMENT_ID, PaymentEvent.AUTH_REJECT);
        ProcessingStateMachineTestSupport.send(sm, PAYMENT_ID, PaymentEvent.FRAUD_CLEAR);
        ProcessingStateMachineTestSupport.send(sm, PAYMENT_ID, PaymentEvent.LIMITS_CLEAR);
        ProcessingStateMachineTestSupport.send(sm, PAYMENT_ID, PaymentEvent.SANCTIONS_PASS);
        ProcessingStateMachineTestSupport.completeFeeCheck(sm, PAYMENT_ID);

        assertThat(dispatcher.decisions).containsExactly(new SagaJoin.JoinDecision(SagaJoin.PROCESSING_JOIN, FAIL));
    }

    @Test
    @DisplayName("dispatching off-thread delivers the event; the machine reaches SETTLEMENT")
    void dispatchingOffThreadReachesSettlement() throws Exception {
        StateMachine<PaymentState, PaymentEvent> sm = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        ExecutorService offThread = Executors.newSingleThreadExecutor();
        try {
            SagaJoinDispatcher hoppingDispatcher = (rootStateMachine, paymentId, join, event) -> offThread.submit(
                    () -> SagaContextProxy.sendEventAndReportAcceptance(rootStateMachine, event, paymentId));
            sm.getStateMachineAccessor()
                    .doWithAllRegions(accessor ->
                            accessor.addStateMachineInterceptor(new ParallelSagaJoinInterceptor(hoppingDispatcher)));

            ProcessingStateMachineTestSupport.completeAuthFraudLimitsSanctions(sm, PAYMENT_ID);
            ProcessingStateMachineTestSupport.completeFeeCheck(sm, PAYMENT_ID);

            offThread.shutdown();
            assertThat(offThread.awaitTermination(5, TimeUnit.SECONDS))
                    .as("dispatch completed")
                    .isTrue();
        } finally {
            offThread.shutdownNow();
        }

        assertThat(sm.getState().getId()).isEqualTo(SETTLEMENT);
    }

    @Test
    @DisplayName(
            "the join is detected on a Reactor parallel thread - proving the async hop is necessary, not incidental")
    void joinDetectedOnNonBlockingThread() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        StateMachine<PaymentState, PaymentEvent> sm = ProcessingStateMachineTestSupport.buildStarted(PAYMENT_ID);
        sm.getStateMachineAccessor()
                .doWithAllRegions(
                        accessor -> accessor.addStateMachineInterceptor(new ParallelSagaJoinInterceptor(dispatcher)));

        ProcessingStateMachineTestSupport.completeAuthFraudLimitsSanctions(sm, PAYMENT_ID);
        ProcessingStateMachineTestSupport.completeFeeCheck(sm, PAYMENT_ID);

        assertThat(dispatcher.threadName).as("real machine, real thread").startsWith("parallel-");
        assertThat(dispatcher.nonBlockingThread)
                .as("this is exactly why dispatch() must hop off this thread before calling sendEvent")
                .isTrue();
        assertThat(sm.getState().getId())
                .as("dispatch never touched the machine")
                .isEqualTo(PROCESSING);
    }
}

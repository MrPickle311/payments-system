package com.example.payments.export.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.example.payments.common.dto.LedgerEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentIdTrackingListenerTest {

  private static final String STEP_NAME = "stepName";
  private static final String METRIC_REASONS = "batch.rollback.reasons";
  private static final String TAG_EXCEPTION = "exception";
  private static final String TAG_STEP = "step";

  private PaymentIdTrackingListener listener;
  private MeterRegistry meterRegistry;
  private StepExecution stepExecution;

  @BeforeEach
  void setUp() {
    meterRegistry = mock(MeterRegistry.class);
    listener = new PaymentIdTrackingListener(meterRegistry);
    stepExecution = new StepExecution(STEP_NAME, null);
    stepExecution.setExecutionContext(new ExecutionContext());
    ReflectionTestUtils.setField(listener, "stepExecution", stepExecution);
  }

  @Test
  void testAfterWrite() {
    LedgerEvent e1 = new LedgerEvent();
    e1.setPaymentId(10L);
    LedgerEvent e2 = new LedgerEvent();
    e2.setPaymentId(20L);
    listener.afterWrite(new Chunk<>(List.of(e1, e2)));
    List<Long> ids = (List<Long>) stepExecution.getExecutionContext().get("processedPaymentIds");
    assertEquals(2, ids.size());
    assertTrue(ids.contains(10L));
    assertTrue(ids.contains(20L));
  }

  @Test
  void testOnWriteError() {
    Counter counter = mock(Counter.class);
    when(meterRegistry.counter(eq(METRIC_REASONS), eq(TAG_EXCEPTION), anyString(), eq(TAG_STEP),
        eq(STEP_NAME))).thenReturn(counter);

    listener.onWriteError(new RuntimeException("Test Error"), new Chunk<>());

    verify(meterRegistry).counter(METRIC_REASONS, TAG_EXCEPTION, "RuntimeException", TAG_STEP,
        STEP_NAME);
    verify(counter).increment();
  }
}

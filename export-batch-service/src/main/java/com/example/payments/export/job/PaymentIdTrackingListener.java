package com.example.payments.export.job;

import com.example.payments.common.dto.LedgerEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.item.Chunk;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@StepScope
public class PaymentIdTrackingListener implements ItemWriteListener<LedgerEvent> {

  private static final String CONTEXT_KEY = "processedPaymentIds";

  @Value("#{stepExecution}")
  private StepExecution stepExecution;

  private final List<Long> processedPaymentIds = new ArrayList<>();
  private final MeterRegistry meterRegistry;

  public PaymentIdTrackingListener(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void afterWrite(Chunk<? extends LedgerEvent> items) {
    items.forEach(item -> processedPaymentIds.add(item.getPaymentId()));
    stepExecution.getExecutionContext().put(CONTEXT_KEY, processedPaymentIds);
    log.debug("Tracked {} paymentIds so far in this step.", processedPaymentIds.size());
  }

  @Override
  public void onWriteError(Exception exception, Chunk<? extends LedgerEvent> items) {
    meterRegistry.counter("batch.rollback.reasons", "exception",
        exception.getClass().getSimpleName(), "step", stepExecution.getStepName()).increment();
    log.warn("Rollback detected due to {}, incrementing metric.",
        exception.getClass().getSimpleName());
  }
}

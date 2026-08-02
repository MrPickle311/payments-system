package com.example.payments.export.job;

import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
@Profile("manager")
@RequiredArgsConstructor
public class PaymentIdTrackingListener implements ItemWriteListener<PaymentExportStaging> {

    private static final String CONTEXT_KEY = "processedPaymentIds";

    @Value("#{stepExecution}")
    private StepExecution stepExecution;

    private final List<Long> processedPaymentIds = new ArrayList<>();
    private final MeterRegistry meterRegistry;
    private final PaymentExportStagingRepository stagingRepository;
    private final ExportProperties exportProperties;

    @Override
    public void afterWrite(Chunk<? extends PaymentExportStaging> items) {
        items.forEach(item -> processedPaymentIds.add(item.getPaymentId()));
        stepExecution.getExecutionContext().put(CONTEXT_KEY, processedPaymentIds);
        log.debug("Tracked {} paymentIds so far in this step.", processedPaymentIds.size());
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends PaymentExportStaging> items) {
        meterRegistry
                .counter(
                        "batch.rollback.reasons",
                        "exception",
                        exception.getClass().getSimpleName(),
                        "step",
                        stepExecution.getStepName())
                .increment();
        log.warn(
                "Rollback detected due to {}, incrementing metric.",
                exception.getClass().getSimpleName());
        List<Long> ids =
                items.getItems().stream().map(PaymentExportStaging::getId).toList();
        stagingRepository.markAsFailedOrNonhealable(ids, exception.getMessage(), exportProperties.getMaxRetryCount());
    }
}

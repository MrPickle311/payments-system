package com.example.payment.infrastructure.external.ledger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.payments.common.sharedkernel.outbox.OutboxEventEntity;
import com.example.payments.common.sharedkernel.outbox.OutboxRepositoryProxy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerPublisherTest {

    private static final String AMOUNT = "100.00";
    private static final String NET_AMOUNT = "98.00";
    private static final String CURRENCY = "USD";

    @Mock
    private OutboxRepositoryProxy outboxRepositoryProxy;

    @InjectMocks
    private LedgerPublisher ledgerPublisher;

    @Test
    void publishEventSuccess() {
        ledgerPublisher.publishEvent(1L, new BigDecimal(AMOUNT), new BigDecimal(NET_AMOUNT), CURRENCY);
        verify(outboxRepositoryProxy).save(any(OutboxEventEntity.class));
    }

    @Test
    void publishEventFailure() {
        doThrow(new RuntimeException("DB down")).when(outboxRepositoryProxy).save(any(OutboxEventEntity.class));
        assertThrows(
                RuntimeException.class,
                () -> ledgerPublisher.publishEvent(1L, new BigDecimal(AMOUNT), new BigDecimal(NET_AMOUNT), CURRENCY));
        verify(outboxRepositoryProxy).save(any(OutboxEventEntity.class));
    }
}

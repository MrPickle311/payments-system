package com.example.payments.ledger.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerRepositoryAdapterTest {

    private static final String AMOUNT_STR = "100.00";
    private static final String CURRENCY_USD = "USD";

    @Mock
    private LedgerEntryRepository repository;

    @InjectMocks
    private LedgerRepositoryAdapter adapter;

    @Test
    void tryInsertReturnsTrueWhenInserted() {
        LedgerEntry entry = createEntry();
        when(repository.tryInsert(
                        entry.getPaymentId(),
                        entry.getGrossAmount(),
                        entry.getNetAmount(),
                        entry.getCurrency(),
                        entry.getTimestamp()))
                .thenReturn(1);

        boolean result = adapter.tryInsert(entry);

        verify(repository, times(1))
                .tryInsert(
                        entry.getPaymentId(),
                        entry.getGrossAmount(),
                        entry.getNetAmount(),
                        entry.getCurrency(),
                        entry.getTimestamp());
        assertThat(result).isTrue();
    }

    @Test
    void tryInsertReturnsFalseOnDuplicate() {
        LedgerEntry entry = createEntry();
        when(repository.tryInsert(
                        entry.getPaymentId(),
                        entry.getGrossAmount(),
                        entry.getNetAmount(),
                        entry.getCurrency(),
                        entry.getTimestamp()))
                .thenReturn(0);

        boolean result = adapter.tryInsert(entry);

        assertThat(result).isFalse();
    }

    private LedgerEntry createEntry() {
        LedgerEntry e = new LedgerEntry();
        e.setPaymentId(1L);
        e.setGrossAmount(new BigDecimal(AMOUNT_STR));
        e.setNetAmount(new BigDecimal(AMOUNT_STR));
        e.setCurrency(CURRENCY_USD);
        e.setTimestamp(LocalDateTime.now());
        return e;
    }
}

package com.example.payments.ledger.infrastructure.persistence;

import com.example.payments.ledger.domain.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerRepositoryAdapterTest {

  private static final String AMOUNT_STR = "100.00";
  private static final String CURRENCY_USD = "USD";

  @Mock
  private LedgerEntryRepository repository;

  @InjectMocks
  private LedgerRepositoryAdapter adapter;

  @Test
  void saveDelegatesToRepository() {
    LedgerEntry entry = createEntry(null);
    LedgerEntry savedEntry = createEntry(100L);

    when(repository.save(entry)).thenReturn(savedEntry);

    LedgerEntry result = adapter.save(entry);

    verify(repository, times(1)).save(entry);
    assertThat(result).isEqualTo(savedEntry);
  }

  private LedgerEntry createEntry(Long id) {
    LedgerEntry e = new LedgerEntry();
    e.setId(id);
    e.setPaymentId(1L);
    e.setGrossAmount(new BigDecimal(AMOUNT_STR));
    e.setNetAmount(new BigDecimal(AMOUNT_STR));
    e.setCurrency(CURRENCY_USD);
    return e;
  }
}

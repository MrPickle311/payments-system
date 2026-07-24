package com.example.payments.ledger.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.application.mapper.LedgerMapper;
import com.example.payments.ledger.domain.LedgerEntry;
import com.example.payments.ledger.domain.LedgerRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final String AMOUNT_STR = "100.00";
    private static final String CURRENCY_USD = "USD";

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerMapper ledgerMapper;

    @InjectMocks
    private LedgerService ledgerService;

    private LedgerEvent event;
    private LedgerEntry entry;

    @BeforeEach
    void setUp() {
        event = new LedgerEvent();
        event.setPaymentId(1L);
        event.setGrossAmount(new BigDecimal(AMOUNT_STR));
        event.setNetAmount(new BigDecimal(AMOUNT_STR));
        event.setCurrency(CURRENCY_USD);

        entry = new LedgerEntry();
        entry.setPaymentId(1L);
        entry.setGrossAmount(new BigDecimal(AMOUNT_STR));
        entry.setNetAmount(new BigDecimal(AMOUNT_STR));
        entry.setCurrency(CURRENCY_USD);
    }

    @Test
    void recordEventSavesLedgerEntry() {
        when(ledgerMapper.toEntity(event)).thenReturn(entry);

        ledgerService.recordEvent(event);

        verify(ledgerMapper, times(1)).toEntity(event);
        verify(ledgerRepository, times(1)).save(entry);
    }
}

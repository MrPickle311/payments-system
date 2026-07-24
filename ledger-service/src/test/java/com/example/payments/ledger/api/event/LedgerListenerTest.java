package com.example.payments.ledger.api.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.application.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerListenerTest {

    private static final String INVALID_JSON = "invalid";

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private LedgerListener ledgerListener;

    private String validJson;
    private LedgerEvent ledgerEvent;

    @BeforeEach
    void setUp() {
        validJson = "{\"paymentId\":1,\"grossAmount\":100.00,\"netAmount\":95.00,\"currency\":\"USD\"}";
        ledgerEvent = new LedgerEvent();
        ledgerEvent.setPaymentId(1L);
        ledgerEvent.setGrossAmount(new BigDecimal("100.00"));
        ledgerEvent.setNetAmount(new BigDecimal("95.00"));
        ledgerEvent.setCurrency("USD");
    }

    @Test
    void consumeValidJsonRecordsEvent() throws Exception {
        when(objectMapper.readValue(validJson, LedgerEvent.class)).thenReturn(ledgerEvent);

        ledgerListener.consume(validJson);

        verify(ledgerService, times(1)).recordEvent(ledgerEvent);
    }

    @Test
    void consumeInvalidJsonHandlesException() throws Exception {
        when(objectMapper.readValue(INVALID_JSON, LedgerEvent.class)).thenThrow(new RuntimeException("error"));

        assertThrows(RuntimeException.class, () -> ledgerListener.consume(INVALID_JSON));

        verify(ledgerService, never()).recordEvent(any());
    }
}

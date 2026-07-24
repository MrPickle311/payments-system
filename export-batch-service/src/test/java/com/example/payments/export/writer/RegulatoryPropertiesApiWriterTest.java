package com.example.payments.export.writer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.dto.RegulatoryReportRequest;
import com.example.payments.export.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.batch.item.Chunk;
import org.springframework.web.client.RestTemplate;

class RegulatoryPropertiesApiWriterTest {

    private static final String API_URL = "http://api.url";

    private RestTemplate restTemplate;
    private RegulatoryApiWriter writer;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        ExportProperties exportProperties = mock(ExportProperties.class);
        ExportProperties.RegulatoryProperties regulatory = mock(ExportProperties.RegulatoryProperties.class);
        when(exportProperties.getRegulatory()).thenReturn(regulatory);
        when(regulatory.getUrl()).thenReturn(API_URL);

        writer = new RegulatoryApiWriter(restTemplate, exportProperties, Mappers.getMapper(PaymentMapper.class));
    }

    @Test
    void testWrite() {
        Chunk<LedgerEvent> chunk = new Chunk<>(List.of(createEvent(100L), createEvent(200L)));
        writer.write(chunk);
        verify(restTemplate).postForLocation(eq(API_URL), any(RegulatoryReportRequest.class));
    }

    private LedgerEvent createEvent(long id) {
        LedgerEvent e = new LedgerEvent();
        e.setPaymentId(id);
        e.setGrossAmount(BigDecimal.TEN);
        e.setNetAmount(BigDecimal.ONE);
        e.setCurrency("USD");
        e.setTimestamp(LocalDateTime.now());
        return e;
    }
}

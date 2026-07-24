package com.example.payments.ledger.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class LedgerMapperTest {

    private final LedgerMapper mapper = Mappers.getMapper(LedgerMapper.class);

    @Test
    void toEntityMapsAllFields() {
        LedgerEvent event = new LedgerEvent();
        event.setPaymentId(1L);
        event.setGrossAmount(new BigDecimal("100.00"));
        event.setNetAmount(new BigDecimal("95.00"));
        event.setCurrency("USD");

        LedgerEntry entry = mapper.toEntity(event);

        assertThat(entry).isNotNull();
        assertThat(entry.getPaymentId()).isEqualTo(event.getPaymentId());
        assertThat(entry.getGrossAmount()).isEqualTo(event.getGrossAmount());
        assertThat(entry.getNetAmount()).isEqualTo(event.getNetAmount());
        assertThat(entry.getCurrency()).isEqualTo(event.getCurrency());
    }

    @Test
    void toEntityNullInput() {
        LedgerEntry entry = mapper.toEntity(null);
        assertThat(entry).isNull();
    }
}

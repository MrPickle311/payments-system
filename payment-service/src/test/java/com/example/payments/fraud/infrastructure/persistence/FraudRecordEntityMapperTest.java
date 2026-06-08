package com.example.payments.fraud.infrastructure.persistence;

import com.example.payments.fraud.domain.FraudRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudRecordEntityMapperTest {

  private final FraudRecordEntityMapper mapper = Mappers.getMapper(FraudRecordEntityMapper.class);

  @Test
  void toEntityAndBack() {
    FraudRecord domain = FraudRecord.builder().id(1L).paymentId(2L).score(10).riskLevel("HIGH")
        .recommendation("REJECT").build();

    FraudRecordJpaEntity entity = mapper.toEntity(domain);
    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals(10, entity.getScore());
    assertEquals("HIGH", entity.getRiskLevel());

    FraudRecord mappedDomain = mapper.toDomain(entity);
    assertEquals(1L, mappedDomain.getId());
    assertEquals(2L, mappedDomain.getPaymentId());
    assertEquals(10, mappedDomain.getScore());
    assertEquals("HIGH", mappedDomain.getRiskLevel());
  }
}

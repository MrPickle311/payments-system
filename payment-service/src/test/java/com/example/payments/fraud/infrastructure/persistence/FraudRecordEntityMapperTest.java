package com.example.payments.fraud.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.payments.fraud.domain.FraudRecord;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class FraudRecordEntityMapperTest {

  private static final String HIGH = "HIGH";
  private static final String REJECT = "REJECT";

  private final FraudRecordEntityMapper mapper = Mappers.getMapper(FraudRecordEntityMapper.class);

  @Test
  void toEntityAndBack() {
    FraudRecord domain = FraudRecord.builder().id(1L).paymentId(2L).score(10).riskLevel(HIGH)
        .recommendation(REJECT).build();

    FraudRecordJpaEntity entity = mapper.toEntity(domain);
    assertEntity(entity);

    FraudRecord mappedDomain = mapper.toDomain(entity);
    assertDomain(mappedDomain);
  }

  private void assertEntity(FraudRecordJpaEntity entity) {
    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals(10, entity.getScore());
    assertEquals(HIGH, entity.getRiskLevel());
  }

  private void assertDomain(FraudRecord domain) {
    assertEquals(1L, domain.getId());
    assertEquals(2L, domain.getPaymentId());
    assertEquals(10, domain.getScore());
    assertEquals(HIGH, domain.getRiskLevel());
  }
}

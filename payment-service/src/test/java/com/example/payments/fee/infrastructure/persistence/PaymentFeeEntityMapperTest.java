package com.example.payments.fee.infrastructure.persistence;

import com.example.payments.fee.domain.PaymentFee;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentFeeEntityMapperTest {

  private final PaymentFeeEntityMapper mapper = Mappers.getMapper(PaymentFeeEntityMapper.class);

  @Test
  void toEntityAndBack() {
    PaymentFee domain = new PaymentFee();
    domain.setId(1L);
    domain.setPaymentId(2L);
    domain.setTotalFee(new BigDecimal("5.00"));

    PaymentFeeJpaEntity entity = mapper.toEntity(domain);
    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals(new BigDecimal("5.00"), entity.getTotalFee());

    PaymentFee mappedDomain = mapper.toDomain(entity);
    assertEquals(1L, mappedDomain.getId());
    assertEquals(2L, mappedDomain.getPaymentId());
    assertEquals(new BigDecimal("5.00"), mappedDomain.getTotalFee());
  }
}

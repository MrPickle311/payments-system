package com.example.payments.fee.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.payments.fee.domain.PaymentFee;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PaymentFeeEntityMapperTest {

  private static final String AMOUNT = "5.00";

  private final PaymentFeeEntityMapper mapper = Mappers.getMapper(PaymentFeeEntityMapper.class);

  @Test
  void toEntityAndBack() {
    PaymentFee domain = new PaymentFee();
    domain.setId(1L);
    domain.setPaymentId(2L);
    domain.setTotalFee(new BigDecimal(AMOUNT));

    PaymentFeeJpaEntity entity = mapper.toEntity(domain);
    assertEntity(entity);

    PaymentFee mappedDomain = mapper.toDomain(entity);
    assertDomain(mappedDomain);
  }

  private void assertEntity(PaymentFeeJpaEntity entity) {
    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals(new BigDecimal(AMOUNT), entity.getTotalFee());
  }

  private void assertDomain(PaymentFee domain) {
    assertEquals(1L, domain.getId());
    assertEquals(2L, domain.getPaymentId());
    assertEquals(new BigDecimal(AMOUNT), domain.getTotalFee());
  }
}

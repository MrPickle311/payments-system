package com.example.payments.payment.infrastructure.persistence;

import com.example.payments.payment.domain.Money;
import com.example.payments.payment.domain.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentEntityMapperTest {

  private final PaymentEntityMapper mapper = new PaymentEntityMapperImpl();

  @Test
  void toEntity_ShouldMapCorrectly() {
    Payment domain = new Payment();
    domain.setId(1L);
    domain.setTransactionId("TX1");
    domain.setState("NEW");
    domain.setMoney(Money.of(new BigDecimal("100.00"), "USD"));

    PaymentJpaEntity entity = mapper.toEntity(domain);

    assertEquals(1L, entity.getId());
    assertEquals("TX1", entity.getTransactionId());
    assertEquals("NEW", entity.getState());
    assertEquals(new BigDecimal("100.00"), entity.getAmount());
    assertEquals("USD", entity.getCurrency());
  }

  @Test
  void toEntity_ShouldHandleNull() {
    assertNull(mapper.toEntity(null));
  }

  @Test
  void toDomain_ShouldMapCorrectly() {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    entity.setId(1L);
    entity.setTransactionId("TX1");
    entity.setState("NEW");
    entity.setAmount(new BigDecimal("100.00"));
    entity.setCurrency("USD");

    Payment domain = mapper.toDomain(entity);

    assertEquals(1L, domain.getId());
    assertEquals("TX1", domain.getTransactionId());
    assertEquals("NEW", domain.getState());
    assertEquals(new BigDecimal("100.00"), domain.getMoney().amount());
    assertEquals("USD", domain.getMoney().currency());
  }

  @Test
  void toDomain_ShouldHandleNull() {
    assertNull(mapper.toDomain(null));
  }
}

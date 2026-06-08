package com.example.payments.payment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.payments.sharedkernel.Money;
import com.example.payments.payment.domain.Payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentEntityMapperTest {

  private static final String TX1 = "TX1";
  private static final String STATE_NEW = "NEW";
  private static final String AMOUNT = "100.00";
  private static final String USD = "USD";

  private final PaymentEntityMapper mapper = new PaymentEntityMapperImpl();

  @Test
  void toEntityShouldMapCorrectly() {
    Payment domain = new Payment();
    domain.setId(1L);
    domain.setTransactionId(TX1);
    domain.setState(STATE_NEW);
    domain.setMoney(Money.of(new BigDecimal(AMOUNT), USD));

    PaymentJpaEntity entity = mapper.toEntity(domain);

    assertEquals(1L, entity.getId());
    assertEquals(TX1, entity.getTransactionId());
    assertEquals(STATE_NEW, entity.getState());
    assertEquals(new BigDecimal(AMOUNT), entity.getAmount());
    assertEquals(USD, entity.getCurrency());
  }

  @Test
  void toEntityShouldHandleNull() {
    assertNull(mapper.toEntity(null));
  }

  @Test
  void toDomainShouldMapCorrectly() {
    PaymentJpaEntity entity = createEntity();
    Payment domain = mapper.toDomain(entity);

    assertEquals(1L, domain.getId());
    assertEquals(TX1, domain.getTransactionId());
    assertEquals(STATE_NEW, domain.getState());
    assertEquals(new BigDecimal(AMOUNT), domain.getMoney().amount());
    assertEquals(USD, domain.getMoney().currency());
  }

  private PaymentJpaEntity createEntity() {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    entity.setId(1L);
    entity.setTransactionId(TX1);
    entity.setState(STATE_NEW);
    entity.setAmount(new BigDecimal(AMOUNT));
    entity.setCurrency(USD);
    return entity;
  }

  @Test
  void toDomainShouldHandleNull() {
    assertNull(mapper.toDomain(null));
  }
}

package com.example.payments.payment.infrastructure.persistence;

import com.example.payments.payment.domain.PaymentHistory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentHistoryEntityMapperTest {

  private final PaymentHistoryEntityMapper mapper = new PaymentHistoryEntityMapperImpl();

  @Test
  void toEntity_ShouldMapCorrectly() {
    PaymentHistory domain = PaymentHistory.builder().id(1L).paymentId(2L).fromState("NEW")
        .toState("PROCESSING").event("INITIATE").build();

    PaymentHistoryJpaEntity entity = mapper.toEntity(domain);

    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals("NEW", entity.getFromState());
    assertEquals("PROCESSING", entity.getToState());
    assertEquals("INITIATE", entity.getEvent());
  }

  @Test
  void toEntity_ShouldHandleNull() {
    assertNull(mapper.toEntity(null));
  }

  @Test
  void toDomain_ShouldMapCorrectly() {
    PaymentHistoryJpaEntity entity = new PaymentHistoryJpaEntity();
    entity.setId(1L);
    entity.setPaymentId(2L);
    entity.setFromState("NEW");
    entity.setToState("PROCESSING");
    entity.setEvent("INITIATE");

    PaymentHistory domain = mapper.toDomain(entity);

    assertEquals(1L, domain.getId());
    assertEquals(2L, domain.getPaymentId());
    assertEquals("NEW", domain.getFromState());
    assertEquals("PROCESSING", domain.getToState());
    assertEquals("INITIATE", domain.getEvent());
  }

  @Test
  void toDomain_ShouldHandleNull() {
    assertNull(mapper.toDomain(null));
  }
}

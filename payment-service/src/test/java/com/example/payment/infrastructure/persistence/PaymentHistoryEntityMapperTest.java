package com.example.payment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.payment.domain.PaymentHistory;
import org.junit.jupiter.api.Test;

class PaymentHistoryEntityMapperTest {

  private static final String STATE_NEW = "NEW";
  private static final String STATE_PROCESSING = "PROCESSING";
  private static final String EVENT_INITIATE = "INITIATE";

  private final PaymentHistoryEntityMapper mapper = new PaymentHistoryEntityMapperImpl();

  @Test
  void toEntityShouldMapCorrectly() {
    PaymentHistory domain = PaymentHistory.builder().id(1L).paymentId(2L).fromState(STATE_NEW)
        .toState(STATE_PROCESSING).event(EVENT_INITIATE).build();

    PaymentHistoryJpaEntity entity = mapper.toEntity(domain);

    assertEquals(1L, entity.getId());
    assertEquals(2L, entity.getPaymentId());
    assertEquals(STATE_NEW, entity.getFromState());
    assertEquals(STATE_PROCESSING, entity.getToState());
    assertEquals(EVENT_INITIATE, entity.getEvent());
  }

  @Test
  void toEntityShouldHandleNull() {
    assertNull(mapper.toEntity(null));
  }

  @Test
  void toDomainShouldMapCorrectly() {
    PaymentHistoryJpaEntity entity = createEntity();
    PaymentHistory domain = mapper.toDomain(entity);

    assertEquals(1L, domain.getId());
    assertEquals(2L, domain.getPaymentId());
    assertEquals(STATE_NEW, domain.getFromState());
    assertEquals(STATE_PROCESSING, domain.getToState());
    assertEquals(EVENT_INITIATE, domain.getEvent());
  }

  private PaymentHistoryJpaEntity createEntity() {
    PaymentHistoryJpaEntity entity = new PaymentHistoryJpaEntity();
    entity.setId(1L);
    entity.setPaymentId(2L);
    entity.setFromState(STATE_NEW);
    entity.setToState(STATE_PROCESSING);
    entity.setEvent(EVENT_INITIATE);
    return entity;
  }

  @Test
  void toDomainShouldHandleNull() {
    assertNull(mapper.toDomain(null));
  }
}

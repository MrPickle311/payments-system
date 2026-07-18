package com.example.payment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payment.domain.Payment;
import com.example.payment.domain.event.PaymentDomainEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryAdapterTest {

  private static final String TX1 = "TX1";

  @Mock
  private SpringDataPaymentRepository repository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private PaymentEntityMapper mapper;

  @InjectMocks
  private PaymentRepositoryAdapter adapter;

  @Test
  void testFindById() {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    Payment domain = new Payment();

    when(repository.findById(1L)).thenReturn(Optional.of(entity));
    when(mapper.toDomain(entity)).thenReturn(domain);

    Optional<Payment> result = adapter.findById(1L);
    assertTrue(result.isPresent());
    assertEquals(domain, result.get());
  }

  @Test
  void testFindByIdWithLock() {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    Payment domain = new Payment();

    when(repository.findByIdWithLock(1L)).thenReturn(Optional.of(entity));
    when(mapper.toDomain(entity)).thenReturn(domain);

    Optional<Payment> result = adapter.findByIdWithLock(1L);
    assertTrue(result.isPresent());
    assertEquals(domain, result.get());
  }

  @Test
  void testFindByTransactionId() {
    PaymentJpaEntity entity = new PaymentJpaEntity();
    Payment domain = new Payment();

    when(repository.findByTransactionId(TX1)).thenReturn(Optional.of(entity));
    when(mapper.toDomain(entity)).thenReturn(domain);

    Optional<Payment> result = adapter.findByTransactionId(TX1);
    assertTrue(result.isPresent());
    assertEquals(domain, result.get());
  }

  @Test
  void testExistsById() {
    when(repository.existsById(1L)).thenReturn(true);
    assertTrue(adapter.existsById(1L));
  }

  @Test
  void testSave() {
    Payment domain = new Payment();
    domain.setId(1L);
    domain.registerCreationEvent();
    PaymentJpaEntity entity = new PaymentJpaEntity();
    PaymentJpaEntity saved = new PaymentJpaEntity();
    Payment savedDomain = new Payment();
    when(mapper.toEntity(domain)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(saved);
    when(mapper.toDomain(saved)).thenReturn(savedDomain);
    assertEquals(savedDomain, adapter.save(domain));
    verify(eventPublisher).publishEvent(any(PaymentDomainEvent.class));
    assertTrue(domain.getDomainEvents().isEmpty());
  }
}

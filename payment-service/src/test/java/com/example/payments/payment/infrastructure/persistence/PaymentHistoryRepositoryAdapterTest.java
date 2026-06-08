package com.example.payments.payment.infrastructure.persistence;

import com.example.payments.payment.domain.PaymentHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryRepositoryAdapterTest {

  @Mock
  private SpringDataPaymentHistoryRepository repository;

  @Mock
  private PaymentHistoryEntityMapper mapper;

  @InjectMocks
  private PaymentHistoryRepositoryAdapter adapter;

  @Test
  void testSave() {
    PaymentHistory domain = PaymentHistory.builder().build();
    PaymentHistoryJpaEntity entity = new PaymentHistoryJpaEntity();
    PaymentHistoryJpaEntity savedEntity = new PaymentHistoryJpaEntity();
    PaymentHistory savedDomain = PaymentHistory.builder().build();

    when(mapper.toEntity(domain)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(savedEntity);
    when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

    PaymentHistory result = adapter.save(domain);
    assertEquals(savedDomain, result);
  }

  @Test
  void testFindByPaymentIdOrderByTimestampAsc() {
    PaymentHistoryJpaEntity entity = new PaymentHistoryJpaEntity();
    PaymentHistory domain = PaymentHistory.builder().build();

    when(repository.findByPaymentIdOrderByTimestampAsc(1L)).thenReturn(List.of(entity));
    when(mapper.toDomain(entity)).thenReturn(domain);

    List<PaymentHistory> result = adapter.findByPaymentIdOrderByTimestampAsc(1L);

    assertEquals(1, result.size());
    assertEquals(domain, result.get(0));
  }
}

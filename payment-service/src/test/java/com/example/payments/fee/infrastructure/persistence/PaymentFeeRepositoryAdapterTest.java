package com.example.payments.fee.infrastructure.persistence;

import com.example.payments.fee.domain.PaymentFee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFeeRepositoryAdapterTest {

  @Mock
  private SpringDataPaymentFeeRepository springDataRepository;

  @Mock
  private PaymentFeeEntityMapper mapper;

  @InjectMocks
  private PaymentFeeRepositoryAdapter adapter;

  @Test
  void save() {
    PaymentFee domain = new PaymentFee();
    PaymentFeeJpaEntity entity = new PaymentFeeJpaEntity();
    PaymentFeeJpaEntity savedEntity = new PaymentFeeJpaEntity();
    PaymentFee savedDomain = new PaymentFee();

    when(mapper.toEntity(domain)).thenReturn(entity);
    when(springDataRepository.save(entity)).thenReturn(savedEntity);
    when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

    PaymentFee result = adapter.save(domain);

    assertEquals(savedDomain, result);
  }

  @Test
  void findByPaymentId() {
    PaymentFeeJpaEntity entity = new PaymentFeeJpaEntity();
    PaymentFee domain = new PaymentFee();

    when(springDataRepository.findByPaymentId(1L)).thenReturn(Optional.of(entity));
    when(mapper.toDomain(entity)).thenReturn(domain);

    Optional<PaymentFee> result = adapter.findByPaymentId(1L);

    assertTrue(result.isPresent());
    assertEquals(domain, result.get());
  }
}

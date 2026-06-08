package com.example.payments.fraud.infrastructure.persistence;

import com.example.payments.fraud.domain.FraudRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudRecordRepositoryAdapterTest {

  @Mock
  private SpringDataFraudRecordRepository repository;

  @Mock
  private FraudRecordEntityMapper mapper;

  @InjectMocks
  private FraudRecordRepositoryAdapter adapter;

  @Test
  void save() {
    FraudRecord domain = new FraudRecord();
    FraudRecordJpaEntity entity = new FraudRecordJpaEntity();
    FraudRecordJpaEntity savedEntity = new FraudRecordJpaEntity();
    FraudRecord savedDomain = new FraudRecord();

    when(mapper.toEntity(domain)).thenReturn(entity);
    when(repository.save(entity)).thenReturn(savedEntity);
    when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

    FraudRecord result = adapter.save(domain);

    assertEquals(savedDomain, result);
  }
}

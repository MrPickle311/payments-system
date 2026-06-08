package com.example.payments.fraud.infrastructure.persistence;

import com.example.payments.fraud.domain.FraudRecord;
import com.example.payments.fraud.domain.FraudRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FraudRecordRepositoryAdapter implements FraudRecordRepository {

  private final SpringDataFraudRecordRepository repository;
  private final FraudRecordEntityMapper mapper;

  @Override
  public FraudRecord save(FraudRecord fraudRecord) {
    FraudRecordJpaEntity entity = mapper.toEntity(fraudRecord);
    FraudRecordJpaEntity saved = repository.save(entity);
    return mapper.toDomain(saved);
  }
}

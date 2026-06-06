package com.example.payments.fraud.infrastructure.persistence;

import com.example.payments.fraud.domain.FraudRecord;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FraudRecordEntityMapper {

    FraudRecordJpaEntity toEntity(FraudRecord domain);

    FraudRecord toDomain(FraudRecordJpaEntity entity);
}

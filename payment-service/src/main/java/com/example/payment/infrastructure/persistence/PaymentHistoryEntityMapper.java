package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.PaymentHistory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentHistoryEntityMapper {

  PaymentHistoryJpaEntity toEntity(PaymentHistory domain);

  PaymentHistory toDomain(PaymentHistoryJpaEntity entity);
}

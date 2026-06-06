package com.example.payments.fee.infrastructure.persistence;

import com.example.payments.fee.domain.PaymentFee;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentFeeEntityMapper {

    PaymentFeeJpaEntity toEntity(PaymentFee domain);

    PaymentFee toDomain(PaymentFeeJpaEntity entity);
}

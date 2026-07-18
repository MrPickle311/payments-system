package com.example.payment.infrastructure.persistence;

import com.example.payments.common.sharedkernel.Money;
import com.example.payment.domain.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {Money.class})
public interface PaymentEntityMapper {

  @Mapping(target = "amount", source = "money.amount")
  @Mapping(target = "currency", source = "money.currency")
  PaymentJpaEntity toEntity(Payment domain);

  @Mapping(target = "money",
      expression = "java(Money.of(entity.getAmount(), entity.getCurrency()))")
  @Mapping(target = "domainEvents", ignore = true)
  Payment toDomain(PaymentJpaEntity entity);
}

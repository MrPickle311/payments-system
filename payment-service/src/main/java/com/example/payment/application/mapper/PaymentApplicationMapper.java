package com.example.payment.application.mapper;

import com.example.payment.application.dto.CreatePaymentRequest;
import com.example.payment.domain.Payment;
import com.example.payment.domain.enums.PaymentState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {PaymentState.class})
public interface PaymentApplicationMapper {

  @Mapping(target = "state", expression = "java(PaymentState.NEW.name())")
  Payment toNewDomainPayment(CreatePaymentRequest request);
}

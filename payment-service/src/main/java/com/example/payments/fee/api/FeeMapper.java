package com.example.payments.fee.api;

import com.example.payments.fee.application.FeeCalculationPort.FeeDto;
import com.example.payments.payment.api.model.ApiPaymentFee;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeeMapper {

  ApiPaymentFee toApi(FeeDto domain);

  default OffsetDateTime map(LocalDateTime time) {
    if (time == null) {
      return null;
    }
    return time.atOffset(ZoneOffset.UTC);
  }
}

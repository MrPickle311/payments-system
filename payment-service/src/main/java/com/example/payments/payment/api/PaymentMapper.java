package com.example.payments.payment.api;

import com.example.payments.payment.api.model.ApiPayment;
import com.example.payments.payment.api.model.ApiPaymentHistory;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistory;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

  ApiPayment toApi(Payment domain);

  ApiPaymentHistory toApi(PaymentHistory domain);

  default OffsetDateTime map(LocalDateTime time) {
    if (time == null) {
      return null;
    }
    return time.atOffset(ZoneOffset.UTC);
  }
}

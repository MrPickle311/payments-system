package com.example.payments.payment.api;

import com.example.payments.payment.api.model.Payment;
import com.example.payments.payment.api.model.PaymentFee;
import com.example.payments.payment.api.model.PaymentHistory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    Payment toApi(com.example.payments.payment.domain.Payment domain);

    PaymentFee toApi(com.example.payments.fee.domain.PaymentFee domain);

    PaymentHistory toApi(com.example.payments.payment.domain.PaymentHistory domain);

    default OffsetDateTime map(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atOffset(ZoneOffset.UTC);
    }
}

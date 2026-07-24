package com.example.payment.api;

import com.example.payment.api.model.ApiPayment;
import com.example.payment.api.model.ApiPaymentHistory;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentHistory;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

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

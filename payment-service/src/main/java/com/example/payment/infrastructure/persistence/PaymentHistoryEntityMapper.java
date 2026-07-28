package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.PaymentHistory;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentHistoryEntityMapper {

    PaymentHistoryJpaEntity toEntity(PaymentHistory domain);

    PaymentHistory toDomain(PaymentHistoryJpaEntity entity);

    default OffsetDateTime map(ZonedDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toOffsetDateTime();
    }

    default ZonedDateTime map(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toZonedDateTime();
    }
}

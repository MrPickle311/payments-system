package com.example.payments.export.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import com.example.payments.export.dto.RegulatoryReportRequest;
import com.example.payments.export.staging.PaymentExportStaging;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = SPRING)
public interface PaymentMapper {

    @Mapping(source = "eventTimestamp", target = "timestamp")
    RegulatoryReportRequest.ExportedPayment stagingToExportedPayment(PaymentExportStaging s);
}

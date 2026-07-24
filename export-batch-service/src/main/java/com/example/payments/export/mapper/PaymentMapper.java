package com.example.payments.export.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.dto.RegulatoryReportRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = SPRING)
public interface PaymentMapper {

    RegulatoryReportRequest.ExportedPayment toExportedPayment(LedgerEvent e);
}

package com.example.payments.export.mapper;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.dto.RegulatoryReportRequest;
import org.mapstruct.Mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(componentModel = SPRING)
public interface PaymentMapper {

  RegulatoryReportRequest.ExportedPayment toExportedPayment(LedgerEvent e);
}

package com.example.payments.mock.regulatory.api.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import com.example.payments.mock.regulatory.api.RegulatoryReportRequest;
import com.example.payments.mock.regulatory.application.dto.RegulatoryReportDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = SPRING)
public interface RegulatoryReportMapper {

    RegulatoryReportDto mapRequest(RegulatoryReportRequest request);
}

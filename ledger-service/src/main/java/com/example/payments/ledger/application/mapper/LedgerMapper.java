package com.example.payments.ledger.application.mapper;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.domain.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LedgerMapper {
  LedgerEntry toEntity(LedgerEvent event);
}

package com.example.payments.ledger.application;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.domain.LedgerEntry;
import com.example.payments.ledger.domain.LedgerRepository;
import com.example.payments.ledger.application.mapper.LedgerMapper;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {
  private final LedgerRepository ledgerRepository;
  private final LedgerMapper ledgerMapper;

  @Transactional
  @Observed(name = "record-ledger-entry")
  public void recordEvent(LedgerEvent event) {
    log.info("[LedgerService] Recording ledger entry for paymentId={}", event.getPaymentId());

    LedgerEntry entry = ledgerMapper.toEntity(event);

    ledgerRepository.save(entry);
    log.info("[LedgerService] Ledger entry saved for paymentId={}", event.getPaymentId());
  }
}

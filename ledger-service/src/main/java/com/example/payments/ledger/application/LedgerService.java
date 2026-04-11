package com.example.payments.ledger.application;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.domain.LedgerEntry;
import com.example.payments.ledger.infrastructure.persistence.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void record(LedgerEvent event) {
        log.info("[LedgerService] Recording ledger entry for paymentId={}", event.getPaymentId());

        LedgerEntry entry = LedgerEntry.builder()
                .paymentId(event.getPaymentId())
                .grossAmount(event.getGrossAmount())
                .netAmount(event.getNetAmount())
                .currency(event.getCurrency())
                .timestamp(event.getTimestamp())
                .build();

        ledgerEntryRepository.save(entry);
        log.info("[LedgerService] Ledger entry saved for paymentId={}", event.getPaymentId());
    }
}

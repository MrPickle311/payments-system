package com.example.payments.ledger.infrastructure.persistence;

import com.example.payments.ledger.domain.LedgerEntry;
import com.example.payments.ledger.domain.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerRepositoryAdapter implements LedgerRepository {

    private final LedgerEntryRepository repository;

    @Override
    public boolean tryInsert(LedgerEntry entry) {
        int inserted = repository.tryInsert(
                entry.getPaymentId(),
                entry.getGrossAmount(),
                entry.getNetAmount(),
                entry.getCurrency(),
                entry.getTimestamp());
        return inserted > 0;
    }
}

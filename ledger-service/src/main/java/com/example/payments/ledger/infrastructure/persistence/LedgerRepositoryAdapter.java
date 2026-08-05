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
    public LedgerEntry save(LedgerEntry entry) {
        return repository.save(entry);
    }
}

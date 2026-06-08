package com.example.payments.ledger.domain;

public interface LedgerRepository {
  LedgerEntry save(LedgerEntry entry);
}

package com.example.payments.ledger.domain;

public interface LedgerRepository {
    /**
     * Atomically inserts the entry, claiming its paymentId as a dedup key.
     * @return true if inserted, false if a ledger entry for this paymentId already exists
     */
    boolean tryInsert(LedgerEntry entry);
}

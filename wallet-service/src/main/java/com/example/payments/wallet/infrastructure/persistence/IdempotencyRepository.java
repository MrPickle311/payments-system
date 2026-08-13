package com.example.payments.wallet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    /**
     * Plain save() on this entity merges instead of inserting (no @Version/Persistable
     * override), so it can't detect a concurrent duplicate. ON CONFLICT DO NOTHING claims the
     * key atomically without throwing: a caught DataIntegrityViolationException would still
     * leave the underlying Postgres transaction aborted, failing every later statement in the
     * same @Transactional method.
     */
    @Modifying
    @Query(value = """
        INSERT INTO idempotency_keys (idempotency_key, status, created_at)
        VALUES (:key, :status, now())
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("status") String status);
}

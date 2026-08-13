package com.example.limits.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    @Modifying
    @Query(value = """
        INSERT INTO idempotency_keys (idempotency_key, status, created_at)
        VALUES (:key, 'PENDING', now())
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(@Param("key") String key);
}

package com.example.payments.fraud.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataFraudRecordRepository extends JpaRepository<FraudRecordJpaEntity, Long> {
}

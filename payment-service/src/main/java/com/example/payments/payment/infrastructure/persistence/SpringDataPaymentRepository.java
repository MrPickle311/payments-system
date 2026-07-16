package com.example.payments.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {

  Optional<PaymentJpaEntity> findByTransactionId(String transactionId);

  List<PaymentJpaEntity> findByState(String state);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
  Optional<PaymentJpaEntity> findByIdWithLock(@Param("id") Long id);

  @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentJpaEntity p WHERE p.state = :state AND p.createdAt >= :since")
  BigDecimal sumAmountByStateAndCreatedAtAfter(@Param("state") String state, @Param("since") LocalDateTime since);
}

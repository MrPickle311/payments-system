package com.example.payments.fee.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SpringDataPaymentFeeRepository extends JpaRepository<PaymentFeeJpaEntity, Long> {
  Optional<PaymentFeeJpaEntity> findByPaymentId(Long paymentId);

  @Query("SELECT COALESCE(SUM(f.totalFee), 0) FROM PaymentFeeJpaEntity f WHERE f.createdAt >= :since")
  BigDecimal sumTotalFeeByCreatedAtAfter(@Param("since") LocalDateTime since);
}

package com.example.payment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPaymentHistoryRepository extends JpaRepository<PaymentHistoryJpaEntity, Long> {

    List<PaymentHistoryJpaEntity> findByPaymentIdOrderByTimestampAsc(Long paymentId);

    /**
     * Every state this payment has ever entered.
     *
     * <p>Used by the deepest recovery layer: region transitions are recorded here as each region
     * finishes, independently of {@code payments.state}, which is only rewritten on root-level
     * changes. So when a pod dies before the composite state is persisted, this is the only
     * remaining evidence that all regions had in fact completed.
     */
    @Query("SELECT DISTINCT h.toState FROM PaymentHistoryJpaEntity h WHERE h.paymentId = :paymentId")
    List<String> findDistinctToStatesByPaymentId(@Param("paymentId") Long paymentId);
}

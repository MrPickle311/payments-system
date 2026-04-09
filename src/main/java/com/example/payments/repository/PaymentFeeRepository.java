package com.example.payments.repository;

import com.example.payments.entity.PaymentFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentFeeRepository extends JpaRepository<PaymentFee, Long> {

    Optional<PaymentFee> findByPaymentId(Long paymentId);
}

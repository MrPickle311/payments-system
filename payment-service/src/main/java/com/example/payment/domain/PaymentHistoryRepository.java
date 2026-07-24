package com.example.payment.domain;

import java.util.List;

public interface PaymentHistoryRepository {
    PaymentHistory save(PaymentHistory history);

    List<PaymentHistory> findByPaymentIdOrderByTimestampAsc(Long paymentId);
}

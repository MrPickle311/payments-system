package com.example.payments.payment.infrastructure.persistence;

import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaymentHistoryRepositoryAdapter implements PaymentHistoryRepository {

    private final SpringDataPaymentHistoryRepository repository;

    @Override
    public PaymentHistory save(PaymentHistory domain) {
        PaymentHistoryJpaEntity entity = PaymentHistoryJpaEntity.builder()
                .paymentId(domain.getPaymentId())
                .fromState(domain.getFromState())
                .toState(domain.getToState())
                .event(domain.getEvent())
                .timestamp(domain.getTimestamp())
                .build();
        
        PaymentHistoryJpaEntity saved = repository.save(entity);
        
        return toDomain(saved);
    }

    @Override
    public List<PaymentHistory> findByPaymentIdOrderByTimestampAsc(Long paymentId) {
        return repository.findByPaymentIdOrderByTimestampAsc(paymentId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private PaymentHistory toDomain(PaymentHistoryJpaEntity entity) {
        return PaymentHistory.builder()
                .id(entity.getId())
                .paymentId(entity.getPaymentId())
                .fromState(entity.getFromState())
                .toState(entity.getToState())
                .event(entity.getEvent())
                .timestamp(entity.getTimestamp())
                .build();
    }
}

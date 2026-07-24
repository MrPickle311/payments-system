package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentHistoryRepositoryAdapter implements PaymentHistoryRepository {

    private final SpringDataPaymentHistoryRepository repository;
    private final PaymentHistoryEntityMapper mapper;

    @Override
    public PaymentHistory save(PaymentHistory domain) {
        PaymentHistoryJpaEntity entity = mapper.toEntity(domain);
        PaymentHistoryJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<PaymentHistory> findByPaymentIdOrderByTimestampAsc(Long paymentId) {
        return repository.findByPaymentIdOrderByTimestampAsc(paymentId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}

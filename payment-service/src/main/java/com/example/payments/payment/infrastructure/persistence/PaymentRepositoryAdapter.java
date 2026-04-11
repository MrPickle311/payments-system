package com.example.payments.payment.infrastructure.persistence;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.common.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<Payment> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByIdWithLock(Long id) {
        return repository.findByIdWithLock(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(this::toDomain);
    }

    @Override
    public Payment save(Payment domain) {
        PaymentJpaEntity entity = toEntity(domain);
        PaymentJpaEntity saved = repository.save(entity);
        
        Payment savedDomain = toDomain(saved);
        
        // Publish domain events
        domain.getDomainEvents().forEach(eventPublisher::publishEvent);
        domain.clearDomainEvents();
        
        return savedDomain;
    }

    @Override
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        return Payment.builder()
                .id(entity.getId())
                .transactionId(entity.getTransactionId())
                .money(Money.of(entity.getAmount(), entity.getCurrency()))
                .state(entity.getState())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PaymentJpaEntity toEntity(Payment domain) {
        return PaymentJpaEntity.builder()
                .id(domain.getId())
                .transactionId(domain.getTransactionId())
                .amount(domain.getMoney().getAmount())
                .currency(domain.getMoney().getCurrency())
                .state(domain.getState())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}

package com.example.payment.infrastructure.persistence;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

  private final SpringDataPaymentRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final PaymentEntityMapper mapper;

  @Override
  public Optional<Payment> findById(Long id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  public Optional<Payment> findByIdWithLock(Long id) {
    return repository.findByIdWithLock(id).map(mapper::toDomain);
  }

  @Override
  public Optional<Payment> findByTransactionId(String transactionId) {
    return repository.findByTransactionId(transactionId).map(mapper::toDomain);
  }

  @Override
  public List<Payment> findByState(String state) {
    return repository.findByState(state).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Payment save(Payment domain) {
    PaymentJpaEntity entity = mapper.toEntity(domain);
    PaymentJpaEntity saved = repository.save(entity);

    Payment savedDomain = mapper.toDomain(saved);

    domain.getDomainEvents().forEach(eventPublisher::publishEvent);
    domain.clearDomainEvents();

    return savedDomain;
  }

  @Override
  public boolean existsById(Long id) {
    return repository.existsById(id);
  }
}

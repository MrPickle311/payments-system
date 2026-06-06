package com.example.payments.fee.infrastructure.persistence;

import com.example.payments.fee.domain.PaymentFee;
import com.example.payments.fee.domain.PaymentFeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("paymentFeeRepositoryAdapter")
@RequiredArgsConstructor
public class PaymentFeeRepositoryAdapter implements PaymentFeeRepository {

    private final SpringDataPaymentFeeRepository springDataRepository;
    private final PaymentFeeEntityMapper mapper;

    @Override
    public PaymentFee save(PaymentFee domain) {
        PaymentFeeJpaEntity entity = mapper.toEntity(domain);
        PaymentFeeJpaEntity savedEntity = springDataRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentFee> findByPaymentId(Long paymentId) {
        return springDataRepository.findByPaymentId(paymentId)
                .map(mapper::toDomain);
    }
}

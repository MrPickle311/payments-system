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

    @Override
    public PaymentFee save(PaymentFee domain) {
        PaymentFeeJpaEntity entity = toEntity(domain);
        PaymentFeeJpaEntity savedEntity = springDataRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentFee> findByPaymentId(Long paymentId) {
        return springDataRepository.findByPaymentId(paymentId)
                .map(this::toDomain);
    }

    private PaymentFeeJpaEntity toEntity(PaymentFee domain) {
        return PaymentFeeJpaEntity.builder()
                .id(domain.getId())
                .paymentId(domain.getPaymentId())
                .grossAmount(domain.getGrossAmount())
                .percentageFee(domain.getPercentageFee())
                .flatFee(domain.getFlatFee())
                .totalFee(domain.getTotalFee())
                .netAmount(domain.getNetAmount())
                .currency(domain.getCurrency())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    private PaymentFee toDomain(PaymentFeeJpaEntity entity) {
        return PaymentFee.builder()
                .id(entity.getId())
                .paymentId(entity.getPaymentId())
                .grossAmount(entity.getGrossAmount())
                .percentageFee(entity.getPercentageFee())
                .flatFee(entity.getFlatFee())
                .totalFee(entity.getTotalFee())
                .netAmount(entity.getNetAmount())
                .currency(entity.getCurrency())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

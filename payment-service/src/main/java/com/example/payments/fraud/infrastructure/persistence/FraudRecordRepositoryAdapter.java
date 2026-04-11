package com.example.payments.fraud.infrastructure.persistence;

import com.example.payments.fraud.domain.FraudRecord;
import com.example.payments.fraud.domain.FraudRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FraudRecordRepositoryAdapter implements FraudRecordRepository {

    private final SpringDataFraudRecordRepository repository;

    @Override
    public FraudRecord save(FraudRecord fraudRecord) {
        FraudRecordJpaEntity entity = FraudRecordJpaEntity.builder()
                .paymentId(fraudRecord.getPaymentId())
                .score(fraudRecord.getScore())
                .riskLevel(fraudRecord.getRiskLevel())
                .recommendation(fraudRecord.getRecommendation())
                .build();
        
        FraudRecordJpaEntity saved = repository.save(entity);
        
        return FraudRecord.builder()
                .id(saved.getId())
                .paymentId(saved.getPaymentId())
                .score(saved.getScore())
                .riskLevel(saved.getRiskLevel())
                .recommendation(saved.getRecommendation())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}

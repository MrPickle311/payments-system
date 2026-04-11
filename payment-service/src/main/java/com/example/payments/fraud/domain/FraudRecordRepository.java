package com.example.payments.fraud.domain;

public interface FraudRecordRepository {
    FraudRecord save(FraudRecord fraudRecord);
}

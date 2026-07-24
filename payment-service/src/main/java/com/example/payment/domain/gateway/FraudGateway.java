package com.example.payment.domain.gateway;

import com.example.payments.fraud.grpc.FraudResponse;

public interface FraudGateway {
    FraudResponse checkFraud(Long paymentId, String amount, String currency);
}

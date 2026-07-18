package com.example.fraud.application;

public record FraudResult(Integer score, String riskLevel, String recommendation) {
    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel);
    }
}

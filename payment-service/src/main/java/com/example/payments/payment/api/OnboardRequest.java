package com.example.payments.payment.api;

public record OnboardRequest(Long payerId, String name, String email) {
}

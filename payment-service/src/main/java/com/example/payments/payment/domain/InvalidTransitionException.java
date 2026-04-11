package com.example.payments.payment.domain;

/**
 * Thrown when a {@link com.example.payments.common.domain.enums.PaymentEvent} is sent to the
 * state machine from a state that has no configured outgoing transition for
 * that event.
 */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}

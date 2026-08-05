package com.example.payment.application.compensation;

/**
 * Thrown when a compensation event cannot be processed.
 * Re-throwing this causes the Kafka container's error handler to route the
 * message to the Dead Letter Topic (DLT) after exhausting retry attempts.
 */
public class CompensationProcessingException extends RuntimeException {

    public CompensationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

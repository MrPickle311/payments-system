package com.example.payments.common.domain.enums;

/**
 * All events that can be sent to the Payment state machine.
 *
 * Each event is only valid from specific source states; attempts to send
 * an event from an illegal state are rejected by the state machine and
 * surfaced as an
 * {@link com.example.payments.payment.domain.InvalidTransitionException}.
 */
public enum PaymentEvent {

    /** Customer initiates the checkout process. NEW → PENDING. */
    INITIATE,

    /**
     * Customer is redirected to a 3-D Secure / external page.
     * PENDING → PENDING (self).
     */
    REDIRECT,

    /**
     * Payment gateway signals successful authorisation.
     * PENDING → AUTHORIZED.
     */
    AUTHORIZE,

    /** Merchant captures the authorised funds. AUTHORIZED → COMPLETED. */
    COMPLETE,

    /**
     * Processing has failed (gateway error, fraud, timeout).
     * PENDING/AUTHORIZED → FAILED.
     */
    FAIL,

    /** Payment cancelled before capture. PENDING/AUTHORIZED → CANCELED. */
    CANCEL,

    /**
     * Refund issued. Only legal from AUTHORIZED (void)
     * or COMPLETED (capture refund).
     * → REFUNDED.
     */
    REFUND,

    // ── Orthogonal region events ─────────────────────────────────────────

    /** Authorization succeeded (inside PROCESSING / Authorization region). */
    AUTH_SUCCESS,

    /** Authorization failed (inside PROCESSING / Authorization region). */
    AUTH_FAIL,

    /**
     * Fraud check cleared the transaction
     * (inside PROCESSING / FraudCheck region).
     */
    FRAUD_CLEAR,

    /**
     * Fraud check flagged the transaction
     * (inside PROCESSING / FraudCheck region).
     */
    FRAUD_ALERT
}

package com.example.payments.enums;

/**
 * All possible states a Payment can occupy in its lifecycle.
 *
 * Lifecycle overview:
 *
 *   NEW ──INITIATE──► PENDING ──AUTHORIZE──► AUTHORIZED ──COMPLETE──► COMPLETED
 *                        │          │               │                      │
 *                     REDIRECT   FAIL/CANCEL      FAIL/CANCEL/REFUND    REFUND
 *                      (self)       │               │                      │
 *                                   ▼               ▼                      ▼
 *                                FAILED          FAILED               REFUNDED
 *                                CANCELED        CANCELED
 *
 * Terminal states (no outgoing transitions): FAILED, CANCELED, REFUNDED.
 * COMPLETED is also terminal once REFUND is no longer applicable (handled by legal transitions).
 */
public enum PaymentState {

    /** Payment has been created but the checkout flow has not started. */
    NEW,

    /** Checkout initiated; awaiting customer action (e.g., 3-D Secure redirect). */
    PENDING,

    /** Funds have been reserved on the customer's account; capture pending. */
    AUTHORIZED,

    /** Funds captured. Digital product can be unlocked and invoice sent. */
    COMPLETED,

    /** Processing failed at any stage (gateway error, fraud, etc.). Terminal. */
    FAILED,

    /** Payment was voluntarily cancelled before capture. Terminal. */
    CANCELED,

    /** Full or partial refund issued. Terminal. */
    REFUNDED
}

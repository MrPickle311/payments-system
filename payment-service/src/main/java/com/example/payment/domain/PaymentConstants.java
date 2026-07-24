package com.example.payment.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class PaymentConstants {

    public static final String FRAUD_SCORE = "fraudScore";
    public static final String FRAUD_RISK = "fraudRisk";
    public static final String EVENT_CREATED = "CREATED";
    public static final String INITIAL_FROM_STATE = "—";
    public static final String PAYMENT_ID = "paymentId";
    public static final String PAYMENT_AMOUNT = "paymentAmount";
    public static final String PAYMENT_CURRENCY = "paymentCurrency";
    public static final String SOURCE_USER_ID = "sourceUserId";
    public static final String TARGET_USER_ID = "targetUserId";
    public static final String SOURCE_CURRENCY = "sourceCurrency";
    public static final String TARGET_CURRENCY = "targetCurrency";
    public static final String FEE_AMOUNT = "feeAmount";
    public static final String IS_RESTORING = "isRestoring";
    public static final String PROCESSING_FEE = "processingFee";
    public static final String NET_AMOUNT = "netAmount";
    public static final String PAYMENT_CREATED_AT = "paymentCreatedAt";
    public static final long INTERNAL_FEE_USER_ID = -1L;

    public static final String AUTH_STATUS = "authStatus";
    public static final String FRAUD_STATUS = "fraudStatus";
    public static final String LIMITS_STATUS = "limitsStatus";
    public static final String SANCTIONS_STATUS = "sanctionsStatus";
    public static final String FEE_STATUS = "feeStatus";

    public static final String STATUS_AUTH_APPROVED = "AUTH_APPROVED";
    public static final String STATUS_AUTH_REJECTED = "AUTH_REJECTED";
    public static final String STATUS_FRAUD_PASSED = "FRAUD_PASSED";
    public static final String STATUS_FRAUD_DETECTED = "FRAUD_DETECTED";
    public static final String STATUS_LIMITS_OK = "LIMITS_OK";
    public static final String STATUS_LIMITS_EXCEEDED = "LIMITS_EXCEEDED";
    public static final String STATUS_LIMITS_RELEASED = "LIMITS_RELEASED";
    public static final String STATUS_SANCTIONS_CLEARED = "SANCTIONS_CLEARED";
    public static final String STATUS_SANCTIONS_HIT = "SANCTIONS_HIT";
    public static final String STATUS_FEE_CALCULATED = "FEE_CALCULATED";
    public static final String STATUS_FEE_CHARGED = "FEE_CHARGED";
    public static final String STATUS_FEE_FAILED = "FEE_FAILED";
    public static final String STATUS_FEE_REFUNDED = "FEE_REFUNDED";
}

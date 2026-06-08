package com.example.payments.payment.domain.enums;


public enum PaymentEvent {

  INITIATE,
  REDIRECT,
  AUTHORIZE,
  COMPLETE,
  FAIL,
  CANCEL,
  REFUND,
  AUTH_SUCCESS,
  AUTH_FAIL,
  FRAUD_CLEAR,
  FRAUD_ALERT
}

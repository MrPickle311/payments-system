package com.example.payments.payment.domain;


public class InvalidTransitionException extends RuntimeException {

  public InvalidTransitionException(String message) {
    super(message);
  }
}

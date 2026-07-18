package com.example.payment.domain;


public class InvalidTransitionException extends RuntimeException {

  public InvalidTransitionException(String message) {
    super(message);
  }
}

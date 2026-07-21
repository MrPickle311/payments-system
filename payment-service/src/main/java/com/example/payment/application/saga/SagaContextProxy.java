package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.example.payment.domain.PaymentConstants.AUTH_STATUS;
import static com.example.payment.domain.PaymentConstants.FEE_AMOUNT;
import static com.example.payment.domain.PaymentConstants.FEE_STATUS;
import static com.example.payment.domain.PaymentConstants.FRAUD_STATUS;
import static com.example.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payment.domain.PaymentConstants.LIMITS_STATUS;
import static com.example.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.PaymentConstants.SANCTIONS_STATUS;
import static com.example.payment.domain.PaymentConstants.SOURCE_CURRENCY;
import static com.example.payment.domain.PaymentConstants.SOURCE_USER_ID;
import static com.example.payment.domain.PaymentConstants.TARGET_CURRENCY;
import static com.example.payment.domain.PaymentConstants.TARGET_USER_ID;

@RequiredArgsConstructor(staticName = "of")
@Slf4j
public class SagaContextProxy {

  private final StateContext<PaymentState, PaymentEvent> context;

  public Long getPaymentId() {
    return context.getExtendedState().get(PAYMENT_ID, Long.class);
  }

  public String getPaymentAmount() {
    Object val = context.getExtendedState().get(PAYMENT_AMOUNT, Object.class);
    return val != null ? String.valueOf(val) : null;
  }

  public void setPaymentAmount(String amount) {
    context.getExtendedState().getVariables().put(PAYMENT_AMOUNT, amount);
  }

  public String getPaymentCurrency() {
    return context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
  }

  public String getSourceCurrency() {
    return context.getExtendedState().get(SOURCE_CURRENCY, String.class);
  }

  public String getTargetCurrency() {
    return context.getExtendedState().get(TARGET_CURRENCY, String.class);
  }

  public Long getSourceUserId() {
    return context.getExtendedState().get(SOURCE_USER_ID, Long.class);
  }

  public Long getTargetUserId() {
    return context.getExtendedState().get(TARGET_USER_ID, Long.class);
  }

  public String getFeeAmount() {
    return context.getExtendedState().get(FEE_AMOUNT, String.class);
  }

  public void setFeeAmount(String feeAmount) {
    context.getExtendedState().getVariables().put(FEE_AMOUNT, feeAmount);
  }

  public void sendEvent(PaymentEvent event) {
    sendEventWithRetries(context.getStateMachine(), event, getPaymentId());
  }

  public static void sendEventWithRetries(StateMachine<PaymentState, PaymentEvent> sm,
      PaymentEvent event, Long paymentId) {
    var message = MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, paymentId).build();
    sm.sendEvent(message);
  }

  public LocalDateTime getPaymentCreatedAt() {
    return context.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);
  }

  public BigDecimal getPaymentAmountAsBigDecimal() {
    return new BigDecimal(context.getExtendedState().get(PAYMENT_AMOUNT, String.class));
  }

  public BigDecimal getNetAmountAsBigDecimal() {
    return context.getExtendedState().get(NET_AMOUNT, BigDecimal.class);
  }

  public Boolean getIsRestoring() {
    return context.getExtendedState().get(IS_RESTORING, Boolean.class);
  }

  public void setAuthStatus(String status) {
    context.getExtendedState().getVariables().put(AUTH_STATUS, status);
  }

  public void setFraudStatus(String status) {
    context.getExtendedState().getVariables().put(FRAUD_STATUS, status);
  }

  public String getLimitsStatus() {
    return context.getExtendedState().get(LIMITS_STATUS, String.class);
  }

  public void setLimitsStatus(String status) {
    context.getExtendedState().getVariables().put(LIMITS_STATUS, status);
  }

  public void setSanctionsStatus(String status) {
    context.getExtendedState().getVariables().put(SANCTIONS_STATUS, status);
  }

  public String getFeeStatus() {
    return context.getExtendedState().get(FEE_STATUS, String.class);
  }

  public void setFeeStatus(String status) {
    context.getExtendedState().getVariables().put(FEE_STATUS, status);
  }
}

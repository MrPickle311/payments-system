package com.example.payment.application.saga;

import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.domain.enums.PaymentState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;

import static com.example.payment.domain.PaymentConstants.AUTH_STATUS;
import static com.example.payment.domain.PaymentConstants.FEE_AMOUNT;
import static com.example.payment.domain.PaymentConstants.FEE_STATUS;
import static com.example.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payment.domain.PaymentConstants.FRAUD_STATUS;
import static com.example.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payment.domain.PaymentConstants.LIMITS_STATUS;
import static com.example.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payment.domain.PaymentConstants.PROCESSING_FEE;
import static com.example.payment.domain.PaymentConstants.SANCTIONS_STATUS;
import static com.example.payment.domain.PaymentConstants.SOURCE_CURRENCY;
import static com.example.payment.domain.PaymentConstants.SOURCE_USER_ID;
import static com.example.payment.domain.PaymentConstants.TARGET_CURRENCY;
import static com.example.payment.domain.PaymentConstants.TARGET_USER_ID;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class SagaContextProxy {

  private final ExtendedState extendedState;
  private final StateMachine<PaymentState, PaymentEvent> stateMachine;

  public static SagaContextProxy of(StateContext<PaymentState, PaymentEvent> context) {
    return new SagaContextProxy(context != null ? context.getExtendedState() : null,
        context != null ? context.getStateMachine() : null);
  }

  public static SagaContextProxy of(StateMachine<PaymentState, PaymentEvent> stateMachine) {
    return new SagaContextProxy(stateMachine != null ? stateMachine.getExtendedState() : null,
        stateMachine);
  }

  public static SagaContextProxy of(ExtendedState extendedState) {
    return new SagaContextProxy(extendedState, null);
  }

  public Long getPaymentId() {
    return extendedState != null ? extendedState.get(PAYMENT_ID, Long.class) : null;
  }

  public String getPaymentAmount() {
    if (extendedState == null) {
      return null;
    }
    Object val = extendedState.get(PAYMENT_AMOUNT, Object.class);
    return val != null ? String.valueOf(val) : null;
  }

  public void setPaymentAmount(String amount) {
    if (extendedState != null) {
      extendedState.getVariables().put(PAYMENT_AMOUNT, amount);
    }
  }

  public String getPaymentCurrency() {
    return extendedState != null ? extendedState.get(PAYMENT_CURRENCY, String.class) : null;
  }

  public String getSourceCurrency() {
    return extendedState != null ? extendedState.get(SOURCE_CURRENCY, String.class) : null;
  }

  public String getTargetCurrency() {
    return extendedState != null ? extendedState.get(TARGET_CURRENCY, String.class) : null;
  }

  public Long getSourceUserId() {
    return extendedState != null ? extendedState.get(SOURCE_USER_ID, Long.class) : null;
  }

  public Long getTargetUserId() {
    return extendedState != null ? extendedState.get(TARGET_USER_ID, Long.class) : null;
  }

  public String getFeeAmount() {
    return extendedState != null ? extendedState.get(FEE_AMOUNT, String.class) : null;
  }

  public void setFeeAmount(String feeAmount) {
    if (extendedState != null) {
      extendedState.getVariables().put(FEE_AMOUNT, feeAmount);
    }
  }

  public void sendEvent(PaymentEvent event) {
    if (stateMachine != null) {
      sendEventWithRetries(stateMachine, event, getPaymentId());
    }
  }

  public static void sendEventWithRetries(StateMachine<PaymentState, PaymentEvent> sm,
      PaymentEvent event, Long paymentId) {
    var message = MessageBuilder.withPayload(event).setHeader(PAYMENT_ID, paymentId).build();
    sm.sendEvent(message);
  }

  public LocalDateTime getPaymentCreatedAt() {
    return extendedState != null ? extendedState.get(PAYMENT_CREATED_AT, LocalDateTime.class)
        : null;
  }

  public BigDecimal getPaymentAmountAsBigDecimal() {
    String amt = extendedState != null ? extendedState.get(PAYMENT_AMOUNT, String.class) : null;
    return amt != null ? new BigDecimal(amt) : null;
  }

  public BigDecimal getNetAmountAsBigDecimal() {
    return extendedState != null ? extendedState.get(NET_AMOUNT, BigDecimal.class) : null;
  }

  public Boolean getIsRestoring() {
    return extendedState != null ? extendedState.get(IS_RESTORING, Boolean.class) : null;
  }

  public void setIsRestoring(Boolean isRestoring) {
    if (extendedState != null) {
      extendedState.getVariables().put(IS_RESTORING, isRestoring);
    }
  }

  public Integer getFraudScore() {
    return extendedState != null ? extendedState.get(FRAUD_SCORE, Integer.class) : null;
  }

  public String getFraudRisk() {
    return extendedState != null ? extendedState.get(FRAUD_RISK, String.class) : null;
  }

  public BigDecimal getProcessingFee() {
    return extendedState != null ? extendedState.get(PROCESSING_FEE, BigDecimal.class) : null;
  }

  public BigDecimal getNetAmount() {
    return extendedState != null ? extendedState.get(NET_AMOUNT, BigDecimal.class) : null;
  }

  public void setAuthStatus(String status) {
    if (extendedState != null) {
      extendedState.getVariables().put(AUTH_STATUS, status);
    }
  }

  public void setFraudStatus(String status) {
    if (extendedState != null) {
      extendedState.getVariables().put(FRAUD_STATUS, status);
    }
  }

  public String getLimitsStatus() {
    return extendedState != null ? extendedState.get(LIMITS_STATUS, String.class) : null;
  }

  public void setLimitsStatus(String status) {
    if (extendedState != null) {
      extendedState.getVariables().put(LIMITS_STATUS, status);
    }
  }

  public void setSanctionsStatus(String status) {
    if (extendedState != null) {
      extendedState.getVariables().put(SANCTIONS_STATUS, status);
    }
  }

  public String getFeeStatus() {
    return extendedState != null ? extendedState.get(FEE_STATUS, String.class) : null;
  }

  public void setFeeStatus(String status) {
    if (extendedState != null) {
      extendedState.getVariables().put(FEE_STATUS, status);
    }
  }
}

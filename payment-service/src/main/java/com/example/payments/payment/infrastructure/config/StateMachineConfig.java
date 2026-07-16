package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fee.application.FeeCalculationPort.FeeBreakdown;
import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.fraud.application.FraudCheckPort.FraudResult;
import com.example.payments.sharedkernel.Money;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import com.example.payments.common.dto.DebitRequest.ReserveFundsCommand;
import com.example.payments.common.dto.DebitRequest.UnreserveFundsCommand;
import com.example.payments.common.dto.DebitRequest.PostJournalEntryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;

import static com.example.payments.payment.domain.PaymentConstants.FRAUD_RISK;
import static com.example.payments.payment.domain.PaymentConstants.FRAUD_SCORE;
import static com.example.payments.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payments.payment.domain.PaymentConstants.NET_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_AMOUNT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CURRENCY;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_ID;
import static com.example.payments.payment.domain.PaymentConstants.PROCESSING_FEE;
import static com.example.payments.payment.domain.PaymentConstants.REJECT_REASON;

import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.guard.Guard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableStateMachineFactory
@Slf4j
@RequiredArgsConstructor
public class StateMachineConfig extends GeneratedStateMachineConfig {

  private static final String UNKNOWN_ERROR = "unknown";
  private static final int DAYS_30 = 30;
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String RISK_MEDIUM = "MEDIUM";
  private static final String RISK_PENDING_REVIEW = "PENDING_REVIEW";
  private static final String RISK_MANUAL_REVIEW = "MANUAL_REVIEW";
  private static final String RISK_LOW = "LOW";
  private static final String RISK_OK = "OK";
  private static final String RISK_HIGH = "HIGH";

  private static final String CMD_RESERVE_FUNDS = "ReserveFundsCommand";
  private static final String CMD_UNRESERVE_FUNDS = "UnreserveFundsCommand";
  private static final String CMD_POST_JOURNAL = "PostJournalEntryCommand";
  private static final String TYPE_BASE = "BASE";
  private static final String TYPE_FEE = "FEE";
  private static final String TOPIC_PAYMENT_EVENTS = "payment-events";

  private final FraudCheckPort fraudCheckService;
  private final FeeCalculationPort feeCalculationService;
  private final WalletClient walletClient;
  private final LedgerPublisher ledgerPublisher;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Override
  protected Guard<PaymentState, PaymentEvent> requireReviewGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_MEDIUM.equalsIgnoreCase(riskLevel)
          || RISK_PENDING_REVIEW.equalsIgnoreCase(riskLevel)
          || RISK_MANUAL_REVIEW.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> riskCheckGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_LOW.equalsIgnoreCase(riskLevel) || STATUS_SUCCESS.equalsIgnoreCase(riskLevel)
          || RISK_OK.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> riskRejectedGuard() {
    return context -> {
      String riskLevel = context.getExtendedState().get(FRAUD_RISK, String.class);
      return RISK_HIGH.equalsIgnoreCase(riskLevel);
    };
  }

  @Override
  protected Guard<PaymentState, PaymentEvent> refundWindowGuard() {
    return context -> {
      LocalDateTime createdAt =
          context.getExtendedState().get(PAYMENT_CREATED_AT, LocalDateTime.class);
      if (createdAt == null) {
        return true;
      }
      return createdAt.isAfter(LocalDateTime.now(ZoneId.systemDefault()).minusDays(DAYS_30));
    };
  }

  @Override
  protected Action<PaymentState, PaymentEvent> evaluateRiskAction() {
    return this::executeEvaluateRisk;
  }

  private void executeEvaluateRisk(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
    FraudResult result = fraudCheckService.evaluate(paymentId, Money.of(amount, curr));
    context.getExtendedState().getVariables().put(FRAUD_SCORE, result.score());
    context.getExtendedState().getVariables().put(FRAUD_RISK, result.riskLevel());
    if (result.recommendation() != null && !"ALLOW".equals(result.recommendation())) {
      context.getExtendedState().getVariables().put(REJECT_REASON, result.recommendation());
    }
  }

  @Override
  protected Action<PaymentState, PaymentEvent> evaluateRiskErrorAction() {
    return context -> log.error("[Action:EvaluateRisk] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationAction() {
    return this::executeFeeCalculation;
  }

  private void executeFeeCalculation(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
    FeeBreakdown breakdown = feeCalculationService.calculate(Money.of(amount, curr));
    context.getExtendedState().getVariables().put(PROCESSING_FEE, breakdown.totalFee().amount());
    context.getExtendedState().getVariables().put(NET_AMOUNT, breakdown.netAmount().amount());

    ReserveFundsCommand cmd = ReserveFundsCommand.builder().paymentId(paymentId).walletId(1L)
        .amount(amount).type(TYPE_BASE).currency(curr).build();
    sendCommand(CMD_RESERVE_FUNDS, cmd);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> feeCalculationErrorAction() {
    return context -> log.error("[Action:FeeCalc] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> reserveFeeAction() {
    return this::executeReserveFee;
  }

  private void executeReserveFee(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal fee = context.getExtendedState().get(PROCESSING_FEE, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);

    ReserveFundsCommand cmd = ReserveFundsCommand.builder().paymentId(paymentId).walletId(1L)
        .amount(fee).type(TYPE_FEE).currency(curr).build();
    sendCommand(CMD_RESERVE_FUNDS, cmd);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> reserveFeeErrorAction() {
    return context -> log.error("[Action:ReserveFee] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> postLedgerAction() {
    return this::executePostLedger;
  }

  private void executePostLedger(StateContext<PaymentState, PaymentEvent> context) {
    Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal gross = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    BigDecimal net = context.getExtendedState().get(NET_AMOUNT, BigDecimal.class);
    BigDecimal fee = context.getExtendedState().get(PROCESSING_FEE, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);

    PostJournalEntryCommand cmd = PostJournalEntryCommand.builder().paymentId(pId).payerWalletId(1L)
        .payeeWalletId(2L).baseAmount(gross).feeAmount(fee).currency(curr).build();
    sendCommand(CMD_POST_JOURNAL, cmd);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> postLedgerErrorAction() {
    return context -> log.error("[Action:PostLedger] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> unreserveBaseAmountAction() {
    return this::executeUnreserveBase;
  }

  private void executeUnreserveBase(StateContext<PaymentState, PaymentEvent> context) {
    Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);

    UnreserveFundsCommand cmd = UnreserveFundsCommand.builder().paymentId(pId).walletId(1L)
        .amount(amount).type(TYPE_BASE).currency(curr).build();
    sendCommand(CMD_UNRESERVE_FUNDS, cmd);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> unreserveBaseAmountErrorAction() {
    return context -> log.error("[Action:UnreserveBase] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> compensateSagaAction() {
    return this::executeCompensateSaga;
  }

  private void executeCompensateSaga(StateContext<PaymentState, PaymentEvent> context) {
    Long paymentId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    BigDecimal fee = context.getExtendedState().get(PROCESSING_FEE, BigDecimal.class);
    String currency = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);

    UnreserveFundsCommand cmdBase = UnreserveFundsCommand.builder().paymentId(paymentId)
        .walletId(1L).amount(amount).type(TYPE_BASE).currency(currency).build();
    sendCommand(CMD_UNRESERVE_FUNDS, cmdBase);

    UnreserveFundsCommand cmdFee = UnreserveFundsCommand.builder().paymentId(paymentId).walletId(1L)
        .amount(fee).type(TYPE_FEE).currency(currency).build();
    sendCommand(CMD_UNRESERVE_FUNDS, cmdFee);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> compensateSagaErrorAction() {
    return context -> log.error("[Action:CompensateSaga] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  private void sendCommand(String type, Object payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      ProducerRecord<String, String> producerRecord = new ProducerRecord<>(TOPIC_PAYMENT_EVENTS, null,
          String.valueOf(payload.hashCode()), json);
      producerRecord.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
      kafkaTemplate.send(producerRecord);
      log.info("[StateMachineConfig] Sent command type={} payload={}", type, json);
    } catch (Exception e) {
      log.error("[StateMachineConfig] Failed to send command type={}: {}", type, e.getMessage());
    }
  }

  @Override
  protected Action<PaymentState, PaymentEvent> settlementAction() {
    return this::executeSettlement;
  }

  private void executeSettlement(StateContext<PaymentState, PaymentEvent> context) {
    Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
    feeCalculationService.saveSettlement(pId, Money.of(amount, curr));
    simulateInternalApiCall(50);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> settlementErrorAction() {
    return context -> log.error("[Action:Settlement] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> completedEntryAction() {
    return this::executeCompletedEntry;
  }

  private void executeCompletedEntry(StateContext<PaymentState, PaymentEvent> context) {
    if (Boolean.TRUE.equals(context.getExtendedState().get(IS_RESTORING, Boolean.class))) {
      return;
    }
    log.info("[Entry:Completed] Payment {} captured!",
        context.getExtendedState().get(PAYMENT_ID, Long.class));
  }

  @Override
  protected Action<PaymentState, PaymentEvent> refundAction() {
    return this::executeRefund;
  }

  private void executeRefund(StateContext<PaymentState, PaymentEvent> context) {
    Long pId = context.getExtendedState().get(PAYMENT_ID, Long.class);
    BigDecimal amount = context.getExtendedState().get(PAYMENT_AMOUNT, BigDecimal.class);
    String curr = context.getExtendedState().get(PAYMENT_CURRENCY, String.class);
    BigDecimal refundFee = new BigDecimal("2.00");
    walletClient.debit(pId, 1L, amount.negate(), curr);
    walletClient.debit(pId, 2L, amount.add(refundFee), curr);
    ledgerPublisher.publishEvent(pId, amount.negate(), amount.add(refundFee).negate(), curr);
  }

  @Override
  protected Action<PaymentState, PaymentEvent> refundErrorAction() {
    return context -> log.error("[Action:Refund] ERROR for payment={}: {}",
        context.getExtendedState().get(PAYMENT_ID, Long.class),
        context.getException() != null ? context.getException().getMessage() : UNKNOWN_ERROR);
  }

  private void simulateInternalApiCall(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}

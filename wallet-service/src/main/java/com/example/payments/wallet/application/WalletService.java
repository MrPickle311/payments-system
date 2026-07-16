package com.example.payments.wallet.application;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitRequest.ReserveFundsCommand;
import com.example.payments.common.dto.DebitRequest.FundsReservedEvent;
import com.example.payments.common.dto.DebitRequest.FundsReservationFailedEvent;
import com.example.payments.common.dto.DebitRequest.UnreserveFundsCommand;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.application.port.WalletAccountPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static com.example.payments.wallet.common.WalletConstants.REF_PREFIX;
import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  public static final long DEFAULT_USER_IDENTIFIER = 1L;
  public static final BigDecimal DEFAULT_MOCK_BALANCE = new BigDecimal("1000.00");
  private static final String CMD_RESERVE_FUNDS = "ReserveFundsCommand";
  private static final String CMD_UNRESERVE_FUNDS = "UnreserveFundsCommand";
  private static final String EVENT_FUNDS_RESERVED = "FundsReservedEvent";
  private static final String EVENT_FUNDS_RESERVATION_FAILED = "FundsReservationFailedEvent";
  private static final String TOPIC_PAYMENT_EVENTS = "payment-events";
  private static final String TYPE_HEADER = "type";
  private static final String DEFAULT_CURRENCY = "USD";
  
  private final WalletAccountPort walletAccountPort;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Transactional
  @Observed(name = "debit-wallet")
  public DebitResponse debit(DebitRequest request) {
    log.info("[WalletService] Processing debit for paymentId={} walletId={} amount={} {}",
        request.getPaymentId(), request.getWalletId(), request.getAmount(), request.getCurrency());
    WalletAccount account = getOrCreateAccount(request.getWalletId(), request.getCurrency());
    if (hasInsufficientFunds(account, request.getAmount())) {
      return buildInsufficientFundsResponse(account, request.getAmount());
    }
    return processSuccessfulDebit(account, request);
  }

  private WalletAccount getOrCreateAccount(Long walletId, String currency) {
    Long id = walletId != null ? walletId : DEFAULT_USER_IDENTIFIER;
    return walletAccountPort.findByUserIdAndCurrency(id, currency)
        .orElseGet(() -> createMockAccount(id, currency));
  }

  private WalletAccount createMockAccount(Long walletId, String currency) {
    WalletAccount newAccount = WalletAccount.builder().id(walletId).userId(walletId)
        .balance(DEFAULT_MOCK_BALANCE).currency(currency).build();
    return walletAccountPort.save(newAccount);
  }

  private boolean hasInsufficientFunds(WalletAccount account, BigDecimal amount) {
    return account.getBalance().compareTo(amount) < 0;
  }

  private DebitResponse buildInsufficientFundsResponse(WalletAccount account, BigDecimal amount) {
    log.warn("[WalletService] Insufficient funds for account userId={} balance={} requested={}",
        account.getUserId(), account.getBalance(), amount);
    return DebitResponse.builder().status(STATUS_INSUFFICIENT_FUNDS).build();
  }

  private DebitResponse processSuccessfulDebit(WalletAccount account, DebitRequest request) {
    account.setBalance(account.getBalance().subtract(request.getAmount()));
    walletAccountPort.save(account);
    log.info("[WalletService] Debit successful for paymentId={} new balance={}",
        request.getPaymentId(), account.getBalance());
    return DebitResponse.builder().status(STATUS_SUCCESS)
        .referenceId(REF_PREFIX + UUID.randomUUID()).build();
  }

  @KafkaListener(topics = TOPIC_PAYMENT_EVENTS, groupId = "wallet-service-group")
  public void consumePaymentEvent(ConsumerRecord<String, String> record) {
    Header typeHeader = record.headers().lastHeader(TYPE_HEADER);
    if (typeHeader == null) {
      log.warn("[WalletService] Missing type header in Kafka record");
      return;
    }
    String type = new String(typeHeader.value(), StandardCharsets.UTF_8);
    try {
      dispatchWalletCommand(type, record.value());
    } catch (Exception e) {
      log.error("[WalletService] Error processing event type={}: {}", type, e.getMessage());
    }
  }

  private void dispatchWalletCommand(String type, String value) throws Exception {
    if (CMD_RESERVE_FUNDS.equals(type)) {
      ReserveFundsCommand cmd = objectMapper.readValue(value, ReserveFundsCommand.class);
      handleReserve(cmd);
    } else if (CMD_UNRESERVE_FUNDS.equals(type)) {
      UnreserveFundsCommand cmd = objectMapper.readValue(value, UnreserveFundsCommand.class);
      handleUnreserve(cmd);
    }
  }

  private void handleReserve(ReserveFundsCommand cmd) {
    log.info("[WalletService] Processing ReserveFundsCommand for paymentId={} amount={} type={}",
        cmd.getPaymentId(), cmd.getAmount(), cmd.getType());
    String currency = cmd.getCurrency() != null ? cmd.getCurrency() : DEFAULT_CURRENCY;
    DebitRequest request = DebitRequest.builder().paymentId(cmd.getPaymentId())
        .amount(cmd.getAmount()).currency(currency).build();
    DebitResponse response = debit(request);

    if (STATUS_SUCCESS.equals(response.getStatus())) {
      publishReservedEvent(cmd, currency);
    } else {
      publishReservationFailedEvent(cmd, currency);
    }
  }

  private void publishReservedEvent(ReserveFundsCommand cmd, String currency) {
    FundsReservedEvent event =
        FundsReservedEvent.builder().paymentId(cmd.getPaymentId()).walletId(cmd.getWalletId())
            .amount(cmd.getAmount()).type(cmd.getType()).currency(currency).build();
    sendEvent(EVENT_FUNDS_RESERVED, event);
  }

  private void publishReservationFailedEvent(ReserveFundsCommand cmd, String currency) {
    FundsReservationFailedEvent event =
        FundsReservationFailedEvent.builder().paymentId(cmd.getPaymentId())
            .walletId(cmd.getWalletId()).reason("Insufficient funds").currency(currency).build();
    sendEvent(EVENT_FUNDS_RESERVATION_FAILED, event);
  }

  private void handleUnreserve(UnreserveFundsCommand cmd) {
    log.info("[WalletService] Processing UnreserveFundsCommand for paymentId={} amount={} type={}",
        cmd.getPaymentId(), cmd.getAmount(), cmd.getType());
    String currency = cmd.getCurrency() != null ? cmd.getCurrency() : DEFAULT_CURRENCY;
    DebitRequest request = DebitRequest.builder().paymentId(cmd.getPaymentId())
        .amount(cmd.getAmount().negate()).currency(currency).build();
    debit(request);

    FundsReservedEvent event =
        FundsReservedEvent.builder().paymentId(cmd.getPaymentId()).walletId(cmd.getWalletId())
            .amount(cmd.getAmount().negate()).type(cmd.getType()).currency(currency).build();
    sendEvent(EVENT_FUNDS_RESERVED, event);
  }

  private void sendEvent(String type, Object payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_PAYMENT_EVENTS, null,
          String.valueOf(payload.hashCode()), json);
      record.headers().add(TYPE_HEADER, type.getBytes(StandardCharsets.UTF_8));
      kafkaTemplate.send(record);
      log.info("[WalletService] Sent event type={} payload={}", type, json);
    } catch (Exception e) {
      log.error("[WalletService] Failed to send event type={}: {}", type, e.getMessage());
    }
  }
}


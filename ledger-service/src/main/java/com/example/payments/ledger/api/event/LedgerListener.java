package com.example.payments.ledger.api.event;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.ledger.application.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.payments.common.dto.DebitRequest.PostJournalEntryCommand;
import com.example.payments.common.dto.DebitRequest.JournalEntryPostedEvent;

import static com.example.payments.ledger.common.LedgerConstants.GROUP_ID;
import static com.example.payments.ledger.common.LedgerConstants.TOPIC;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerListener {
  private static final String TOPIC_PAYMENT_EVENTS = "payment-events";
  private static final String CMD_POST_JOURNAL = "PostJournalEntryCommand";
  private static final String EVENT_JOURNAL_POSTED = "JournalEntryPostedEvent";
  private static final String TYPE_HEADER = "type";

  private final LedgerService ledgerService;
  private final ObjectMapper objectMapper;
  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
  public void consume(String eventJson) {
    log.info("[LedgerService] Consumed ledger event from Kafka: {}", eventJson);
    try {
      LedgerEvent event = objectMapper.readValue(eventJson, LedgerEvent.class);
      ledgerService.recordEvent(event);
    } catch (Exception e) {
      log.error("[LedgerService] Failed to deserialize or record ledger event: {}", e.getMessage());
    }
  }

  @KafkaListener(topics = TOPIC_PAYMENT_EVENTS, groupId = "ledger-saga-group")
  public void consumeSagaEvent(ConsumerRecord<String, String> record) {
    Header typeHeader = record.headers().lastHeader(TYPE_HEADER);
    if (typeHeader == null) {
      log.warn("[LedgerListener] Missing type header in Kafka record");
      return;
    }
    String type = new String(typeHeader.value(), StandardCharsets.UTF_8);
    try {
      dispatchSagaCommand(type, record.value());
    } catch (Exception e) {
      log.error("[LedgerListener] Error processing event type={}: {}", type, e.getMessage());
    }
  }

  private void dispatchSagaCommand(String type, String value) throws Exception {
    if (CMD_POST_JOURNAL.equals(type)) {
      PostJournalEntryCommand cmd = objectMapper.readValue(value, PostJournalEntryCommand.class);
      handlePostJournalEntry(cmd);
    }
  }

  private void handlePostJournalEntry(PostJournalEntryCommand cmd) {
    log.info("[LedgerListener] Processing PostJournalEntryCommand for paymentId={}",
        cmd.getPaymentId());
    String currency = cmd.getCurrency() != null ? cmd.getCurrency() : "USD";
    BigDecimal netAmount = cmd.getBaseAmount().subtract(cmd.getFeeAmount());

    LedgerEvent event =
        LedgerEvent.builder().paymentId(cmd.getPaymentId()).grossAmount(cmd.getBaseAmount())
            .netAmount(netAmount).currency(currency).timestamp(LocalDateTime.now()).build();
    ledgerService.recordEvent(event);

    JournalEntryPostedEvent response = JournalEntryPostedEvent.builder()
        .paymentId(cmd.getPaymentId()).entryId(System.currentTimeMillis()).build();
    sendEvent(EVENT_JOURNAL_POSTED, response);
  }

  private void sendEvent(String type, Object payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_PAYMENT_EVENTS, null,
          String.valueOf(payload.hashCode()), json);
      record.headers().add(TYPE_HEADER, type.getBytes(StandardCharsets.UTF_8));
      kafkaTemplate.send(record);
      log.info("[LedgerListener] Sent event type={} payload={}", type, json);
    } catch (Exception e) {
      log.error("[LedgerListener] Failed to send event type={}: {}", type, e.getMessage());
    }
  }
}

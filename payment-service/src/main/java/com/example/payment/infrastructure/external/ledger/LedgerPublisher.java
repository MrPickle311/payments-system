package com.example.payment.infrastructure.external.ledger;

import com.example.payments.common.sharedkernel.outbox.OutboxEventEntity;
import com.example.payments.common.sharedkernel.outbox.OutboxRepositoryProxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerPublisher {

    private final OutboxRepositoryProxy outboxRepositoryProxy;

    public void publishEvent(Long paymentId, BigDecimal gross, BigDecimal net, String currency) {
        if (paymentId == null) {
            log.error("[LedgerPublisher] Cannot publish outbox ledger event with null paymentId");
            return;
        }
        log.debug("[LedgerPublisher] Saving outbox ledger event for paymentId={}", paymentId);
        outboxRepositoryProxy.save(OutboxEventEntity.builder()
                .aggregateId(String.valueOf(paymentId))
                .aggregateType("Payment")
                .eventType("LEDGER_EVENT")
                .payload(toJsonPayload(paymentId, gross, net, currency))
                .topic("payment-ledger-events")
                .processed(false)
                .build());
    }

    private static String toJsonPayload(Long paymentId, BigDecimal gross, BigDecimal net, String currency) {
        return String.format(
                "{\"paymentId\":%d,\"grossAmount\":%s,\"netAmount\":%s,\"currency\":\"%s\",\"timestamp\":\"%s\"}",
                paymentId, gross, net, currency, LocalDateTime.now(ZoneId.systemDefault()));
    }
}

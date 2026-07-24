package com.example.payments.outbox;

import java.util.List;

import com.example.payments.common.sharedkernel.outbox.OutboxEventEntity;
import com.example.payments.common.sharedkernel.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPollerService {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void pollAndPublish() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findTop100ByProcessedFalseOrderByCreatedAtAsc();
        if (!pendingEvents.isEmpty()) {
            log.debug("[OutboxPoller] Found {} pending outbox events to publish.", pendingEvents.size());
            pendingEvents.forEach(this::publishSingleEvent);
        }
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        try {
            kafkaTemplate
                    .send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get();
            event.setProcessed(true);
            log.info(
                    "[OutboxPoller] Successfully published outbox event id={} to topic={}",
                    event.getId(),
                    event.getTopic());
        } catch (InterruptedException e) {
            log.warn("Thread interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[OutboxPoller] Failed to publish outbox event id={}: {}", event.getId(), e.getMessage(), e);
        }
        finally {
            outboxRepository.save(event);
        }
    }
}

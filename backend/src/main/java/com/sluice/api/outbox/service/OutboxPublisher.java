package com.sluice.api.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.messaging.RunQueuePublisher;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import com.sluice.api.webhook.service.WebhookDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final RunQueuePublisher queue;
    private final WebhookDeliveryService webhooks;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository events, RunQueuePublisher queue,
                           WebhookDeliveryService webhooks, ObjectMapper objectMapper,
                           @org.springframework.beans.factory.annotation.Value("${sluice.outbox.batch-size:25}") int batchSize) {
        this.events = events;
        this.queue = queue;
        this.webhooks = webhooks;
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, Math.min(100, batchSize));
    }

    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = events.lockNextBatch(batchSize);
        for (OutboxEvent event : batch) {
            try {
                if ("run.queued".equals(event.getEventType())) {
                    queue.publish(objectMapper.readValue(event.getPayload(), JobMessage.class));
                } else if (event.getEventType().startsWith("run.")) {
                    webhooks.enqueue(event);
                } else {
                    throw new IllegalArgumentException("Unsupported outbox event type");
                }
                event.markPublished();
            } catch (Exception exception) {
                event.markFailed("outbox_dispatch_failed");
            }
            events.save(event);
        }
        return batch.size();
    }
}

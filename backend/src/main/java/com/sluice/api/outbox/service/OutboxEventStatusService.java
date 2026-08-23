package com.sluice.api.outbox.service;

import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxEventStatusService {
    private final OutboxEventRepository events;

    public OutboxEventStatusService(OutboxEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        events.findById(eventId).ifPresent(event -> {
            event.markPublished();
            events.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String message) {
        events.findById(eventId).ifPresent(event -> {
            event.markFailed(message);
            events.save(event);
        });
    }
}

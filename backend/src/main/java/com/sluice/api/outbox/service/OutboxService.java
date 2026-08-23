package com.sluice.api.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.job.domain.Job;
import com.sluice.api.messaging.JobPublisher;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final JobPublisher publisher;
    private final ObjectMapper objectMapper;
    private final OutboxEventStatusService status;

    public OutboxService(OutboxEventRepository events, JobPublisher publisher, ObjectMapper objectMapper,
                         OutboxEventStatusService status) {
        this.events = events;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.status = status;
    }

    @Transactional
    public OutboxEvent createRunQueuedEvent(Job job) {
        try {
            String payload = objectMapper.writeValueAsString(new JobMessage(job.getId(), job.getAssetId()));
            return events.save(new OutboxEvent(UUID.randomUUID(), "run.queued", "JOB", job.getId(), payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize the run queue event", exception);
        }
    }

    public void publishAfterCommit(OutboxEvent event, JobMessage message) {
        Runnable publish = () -> {
            try {
                publisher.publishJob(message);
                status.markPublished(event.getId());
            } catch (RuntimeException exception) {
                status.markFailed(event.getId(), exception.getMessage());
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

}

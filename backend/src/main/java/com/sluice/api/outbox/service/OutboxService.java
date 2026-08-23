package com.sluice.api.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.job.domain.Job;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEvent createRunQueuedEvent(Job job) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new com.sluice.api.messaging.dto.JobMessage(job.getId(), job.getAssetId()));
            return events.save(new OutboxEvent(UUID.randomUUID(), job.getProjectId(),
                    "run.queued", "JOB", job.getId(), payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize the run queue event", exception);
        }
    }

    @Transactional
    public OutboxEvent createTerminalEvent(Job job, UUID outputAssetId) {
        if (job.getWebhookEndpointId() == null) return null;
        UUID eventId = UUID.randomUUID();
        String payload;
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("eventId", eventId);
            body.put("timestamp", java.time.Instant.now());
            body.put("projectId", job.getProjectId());
            body.put("runId", job.getId());
            body.put("status", job.getStatus().name());
            body.put("webhookEndpointId", job.getWebhookEndpointId());
            body.put("outputs", outputAssetId == null ? java.util.List.of() : java.util.List.of(
                    java.util.Map.of("assetId", outputAssetId)));
            body.put("metrics", java.util.Map.of(
                    "inputBytes", job.getInputBytes() == null ? 0 : job.getInputBytes(),
                    "outputBytes", job.getOutputBytes() == null ? 0 : job.getOutputBytes(),
                    "bytesSaved", job.getBytesSaved() == null ? 0 : job.getBytesSaved()));
            if (job.getErrorCode() != null) {
                body.put("error", java.util.Map.of("code", job.getErrorCode(), "message", job.getErrorMessage()));
            }
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize terminal run event", exception);
        }
        return events.save(new OutboxEvent(eventId, job.getProjectId(),
                "run." + job.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                "JOB", job.getId(), payload));
    }

    /** Compatibility hook retained for L-03 callers; durable polling owns publication. */
    public void publishAfterCommit(OutboxEvent event, com.sluice.api.messaging.dto.JobMessage message) {
        // Intentionally empty. The committed PENDING row is the publication trigger.
    }
}

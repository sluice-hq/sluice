package com.sluice.api.webhook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "endpoint_id", nullable = false) private UUID endpointId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "delivered_at") private Instant deliveredAt;

    protected WebhookDelivery() {}
    public WebhookDelivery(UUID id, UUID eventId, UUID endpointId, UUID projectId, UUID jobId,
                           String eventType, String payload, Instant now) {
        this.id = id; this.eventId = eventId; this.endpointId = endpointId; this.projectId = projectId;
        this.jobId = jobId; this.eventType = eventType; this.payload = payload; this.status = "PENDING";
        this.nextAttemptAt = now; this.createdAt = now;
    }

    public int beginAttempt() { return ++attemptCount; }
    public void delivered() { status = "DELIVERED"; deliveredAt = Instant.now(); lastError = null; }
    public void retry(String error) {
        status = "PENDING"; lastError = safe(error);
        nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(8, attemptCount - 1)));
    }
    public void fail(String error) { status = "FAILED"; lastError = safe(error); }
    private String safe(String value) { return value == null ? null : value.substring(0, Math.min(500, value.length())); }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getEndpointId() { return endpointId; }
    public UUID getProjectId() { return projectId; }
    public UUID getJobId() { return jobId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}

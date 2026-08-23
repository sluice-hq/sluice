package com.sluice.api.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, UUID projectId, String eventType, String aggregateType, UUID aggregateId, String payload) {
        this.id = id;
        this.projectId = projectId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public OutboxEvent(UUID id, String eventType, String aggregateType, UUID aggregateId, String payload) {
        this(id, null, eventType, aggregateType, aggregateId, payload);
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public UUID getProjectId() { return projectId; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.attempts++;
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.nextAttemptAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "PENDING";
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(500, error.length()));
        long delaySeconds = Math.min(300, 1L << Math.min(8, Math.max(0, attempts - 1)));
        this.nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    }
}

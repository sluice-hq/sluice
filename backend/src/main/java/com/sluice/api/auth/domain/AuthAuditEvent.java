package com.sluice.api.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_events")
public class AuthAuditEvent {
    @Id
    private UUID id;
    private String eventType;
    private String outcome;
    private String subjectHash;
    private String clientHash;
    private Instant createdAt;

    protected AuthAuditEvent() {}

    public AuthAuditEvent(UUID id, String eventType, String outcome, String subjectHash,
                          String clientHash, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.outcome = outcome;
        this.subjectHash = subjectHash;
        this.clientHash = clientHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getOutcome() { return outcome; }
    public String getSubjectHash() { return subjectHash; }
    public String getClientHash() { return clientHash; }
    public Instant getCreatedAt() { return createdAt; }
}

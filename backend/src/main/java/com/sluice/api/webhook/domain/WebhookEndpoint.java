package com.sluice.api.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "callback_url", nullable = false, length = 2048) private String callbackUrl;
    @Column(name = "secret_value", nullable = false, length = 255) private String secretValue;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WebhookEndpoint() {}
    public WebhookEndpoint(UUID id, UUID projectId, String callbackUrl, String secretValue, Instant createdAt) {
        this.id = id; this.projectId = projectId; this.callbackUrl = callbackUrl;
        this.secretValue = secretValue; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getCallbackUrl() { return callbackUrl; }
    public String getSecretValue() { return secretValue; }
    public Instant getCreatedAt() { return createdAt; }
}

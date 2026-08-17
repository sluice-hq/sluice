package com.sluice.api.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    private UUID id;
    private UUID projectId;
    private String keyHash;
    private String name;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    protected ApiKey() {}

    public ApiKey(UUID id, UUID projectId, String keyHash, String name, Instant createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.keyHash = keyHash;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getKeyHash() { return keyHash; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    
    public boolean isValid() {
        return revokedAt == null;
    }
}

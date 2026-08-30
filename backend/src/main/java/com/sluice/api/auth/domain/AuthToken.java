package com.sluice.api.auth.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    private UUID id;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private AuthTokenPurpose purpose;
    private String tokenHash;
    private Instant expiresAt;
    private Instant consumedAt;
    private Instant createdAt;

    protected AuthToken() {}

    public AuthToken(UUID id, UUID userId, AuthTokenPurpose purpose, String tokenHash,
                     Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AuthTokenPurpose getPurpose() { return purpose; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void consume(Instant now) { this.consumedAt = now; }
}

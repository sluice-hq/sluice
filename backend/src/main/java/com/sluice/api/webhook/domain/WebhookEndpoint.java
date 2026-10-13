package com.sluice.api.webhook.domain;

import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "callback_url", nullable = false, length = 2048) private String callbackUrl;
    @Column(name = "secret_value", nullable = false, length = 255) private String legacySecretValue;
    @Column(name = "secret_ciphertext") private byte[] secretCiphertext;
    @Column(name = "secret_nonce") private byte[] secretNonce;
    @Column(name = "wrapped_data_key") private byte[] wrappedDataKey;
    @Column(name = "wrapping_key_id", length = 1024) private String wrappingKeyId;
    @Column(name = "encryption_version") private Short encryptionVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WebhookEndpoint() {}
    public WebhookEndpoint(UUID id, UUID projectId, String callbackUrl,
                           WebhookSecretCipher.Envelope secret, Instant createdAt) {
        this(id, projectId, callbackUrl, secret, "", createdAt);
    }

    public WebhookEndpoint(UUID id, UUID projectId, String callbackUrl,
                           WebhookSecretCipher.Envelope secret, String legacySecretValue, Instant createdAt) {
        this.id = id; this.projectId = projectId; this.callbackUrl = callbackUrl;
        this.legacySecretValue = legacySecretValue == null ? "" : legacySecretValue; this.createdAt = createdAt;
        protect(secret, false);
    }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getCallbackUrl() { return callbackUrl; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean hasProtectedSecret() {
        return encryptionVersion != null && secretCiphertext != null && secretNonce != null
                && wrappedDataKey != null && wrappingKeyId != null;
    }

    public String getLegacySecretForMigration() { return legacySecretValue; }
    public byte[] getSecretCiphertext() { return copy(secretCiphertext); }
    public byte[] getSecretNonce() { return copy(secretNonce); }
    public byte[] getWrappedDataKey() { return copy(wrappedDataKey); }
    public String getWrappingKeyId() { return wrappingKeyId; }
    public Short getEncryptionVersion() { return encryptionVersion; }

    public void protect(WebhookSecretCipher.Envelope secret) {
        protect(secret, true);
    }

    public void protect(WebhookSecretCipher.Envelope secret, boolean clearLegacySecret) {
        if (secret == null) throw new IllegalArgumentException("Protected webhook secret is required");
        this.encryptionVersion = secret.encryptionVersion();
        this.secretCiphertext = secret.ciphertext();
        this.secretNonce = secret.nonce();
        this.wrappedDataKey = secret.wrappedDataKey();
        this.wrappingKeyId = secret.wrappingKeyId();
        if (clearLegacySecret) this.legacySecretValue = "";
    }

    public void clearLegacySecret() { this.legacySecretValue = ""; }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}

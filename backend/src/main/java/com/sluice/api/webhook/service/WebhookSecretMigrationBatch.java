package com.sluice.api.webhook.service;

import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

@Service
@ConditionalOnApiRuntime
public class WebhookSecretMigrationBatch {
    private final WebhookEndpointRepository endpoints;
    private final WebhookSecretCipher secrets;
    private final SluiceMetrics metrics;
    private final int batchSize;
    private final WebhookSecretMigrationMode migrationMode;

    public WebhookSecretMigrationBatch(WebhookEndpointRepository endpoints, WebhookSecretCipher secrets,
                                       SluiceMetrics metrics,
                                       @Value("${sluice.webhooks.encryption.migration-batch-size:100}") int batchSize,
                                       @Value("${sluice.webhooks.encryption.migration-mode:protected}") String migrationMode) {
        this.endpoints = endpoints;
        this.secrets = secrets;
        this.metrics = metrics;
        this.batchSize = Math.max(1, Math.min(1000, batchSize));
        this.migrationMode = WebhookSecretMigrationMode.parse(migrationMode);
    }

    @Transactional
    public int migrateNextBatch() {
        List<WebhookEndpoint> legacy = endpoints.lockLegacyBatch(PageRequest.of(0, batchSize));
        for (WebhookEndpoint endpoint : legacy) {
            byte[] plaintext = decodeLegacySecret(endpoint.getLegacySecretForMigration());
            try {
                WebhookSecretCipher.Envelope envelope = secrets.encrypt(
                        endpoint.getProjectId(), endpoint.getId(), plaintext);
                verifyEnvelope(endpoint, envelope, plaintext);
                endpoint.protect(envelope,
                        !migrationMode.retainsLegacyPlaintext());
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
        endpoints.flush();
        if (!legacy.isEmpty()) {
            metrics.webhookSecretMigration("migrated", legacy.size());
        }
        return legacy.size();
    }

    @Transactional
    public int purgeNextLegacyBatch() {
        if (migrationMode.retainsLegacyPlaintext()) return 0;
        List<WebhookEndpoint> protectedEndpoints = endpoints.lockProtectedBatchWithLegacySecret(
                PageRequest.of(0, batchSize));
        for (WebhookEndpoint endpoint : protectedEndpoints) {
            byte[] plaintext = decodeLegacySecret(endpoint.getLegacySecretForMigration());
            try {
                verifyEnvelope(endpoint, new WebhookSecretCipher.Envelope(endpoint.getEncryptionVersion(),
                        endpoint.getSecretCiphertext(), endpoint.getSecretNonce(), endpoint.getWrappedDataKey(),
                        endpoint.getWrappingKeyId()), plaintext);
                endpoint.clearLegacySecret();
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
        endpoints.flush();
        if (!protectedEndpoints.isEmpty()) {
            metrics.webhookSecretMigration("purged", protectedEndpoints.size());
        }
        return protectedEndpoints.size();
    }

    public boolean retainsLegacyPlaintext() {
        return migrationMode.retainsLegacyPlaintext();
    }

    private void verifyEnvelope(WebhookEndpoint endpoint, WebhookSecretCipher.Envelope envelope, byte[] plaintext) {
        byte[] verified = secrets.decrypt(endpoint.getProjectId(), endpoint.getId(), envelope.encryptionVersion(),
                envelope.ciphertext(), envelope.nonce(), envelope.wrappedDataKey(), envelope.wrappingKeyId());
        try {
            if (!MessageDigest.isEqual(plaintext, verified)) {
                throw new IllegalStateException("Webhook secret encryption verification failed");
            }
        } finally {
            Arrays.fill(verified, (byte) 0);
        }
    }

    private byte[] decodeLegacySecret(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("Unexpected legacy secret length");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("A legacy webhook secret cannot be migrated safely", exception);
        }
    }
}

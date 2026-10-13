package com.sluice.api.webhook.service;

import com.sluice.api.support.SluiceIntegrationTest;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SluiceIntegrationTest
@TestPropertySource(properties = "sluice.runtime.mode=api")
class WebhookSecretMigrationIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebhookSecretMigrationBatch migration;
    @Autowired private WebhookEndpointRepository endpoints;
    @Autowired private WebhookSecretCipher secrets;

    @Test
    @Transactional
    void backfillsLegacyRowsAndTombstonesPlaintext() {
        UUID projectId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        byte[] rawSecret = new byte[32];
        java.util.Arrays.fill(rawSecret, (byte) 11);
        String legacySecret = Base64.getEncoder().encodeToString(rawSecret);
        jdbc.update("insert into projects (id, name, created_at) values (?, ?, ?)",
                projectId, "Webhook migration", Timestamp.from(Instant.now()));
        jdbc.update("insert into webhook_endpoints (id, project_id, callback_url, secret_value, created_at) " +
                        "values (?, ?, ?, ?, ?)", endpointId, projectId, "https://hooks.example.test/run",
                legacySecret, Timestamp.from(Instant.now()));

        assertEquals(1, migration.migrateNextBatch());

        assertEquals("", jdbc.queryForObject("select secret_value from webhook_endpoints where id = ?",
                String.class, endpointId));
        assertNotNull(jdbc.queryForObject("select secret_ciphertext from webhook_endpoints where id = ?",
                byte[].class, endpointId));
        assertFalse(jdbc.queryForObject(
                "select encode(secret_ciphertext, 'base64') = ? from webhook_endpoints where id = ?",
                Boolean.class, legacySecret, endpointId));

        WebhookEndpoint endpoint = endpoints.findById(endpointId).orElseThrow();
        assertArrayEquals(rawSecret, secrets.decrypt(projectId, endpointId, endpoint.getEncryptionVersion(),
                endpoint.getSecretCiphertext(), endpoint.getSecretNonce(), endpoint.getWrappedDataKey(),
                endpoint.getWrappingKeyId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret_ciphertext", "secret_nonce", "wrapped_data_key", "wrapping_key_id",
            "encryption_version"})
    @Transactional
    void rejectsIncompleteEncryptedRows(String column) {
        UUID projectId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        jdbc.update("insert into projects (id, name, created_at) values (?, ?, ?)",
                projectId, "Envelope constraint", Timestamp.from(Instant.now()));
        WebhookEndpoint endpoint = new WebhookEndpoint(endpointId, projectId, "https://hooks.example.test/run",
                secrets.encrypt(projectId, endpointId, new byte[32]), Instant.now());
        endpoints.saveAndFlush(endpoint);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update webhook_endpoints set " + column + " = null where id = ?", endpointId));
    }
}

package com.sluice.api.webhook.service;

import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookSecretMigrationBatchTest {
    @Test
    void protectedModeEncryptsThenPurgesCompatibilityPlaintext() {
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        WebhookSecretCipher cipher = mock(WebhookSecretCipher.class);
        SluiceMetrics metrics = mock(SluiceMetrics.class);
        WebhookEndpoint legacy = legacyEndpoint();
        WebhookEndpoint dualWritten = legacyEndpoint();
        WebhookSecretCipher.Envelope envelope = envelope();
        when(repository.lockLegacyBatch(any(Pageable.class))).thenReturn(List.of(legacy));
        when(repository.lockProtectedBatchWithLegacySecret(any(Pageable.class))).thenReturn(List.of(dualWritten));
        when(cipher.encrypt(any(), any(), any())).thenReturn(envelope);
        when(cipher.decrypt(any(), any(), anyShort(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> new byte[32]);
        when(dualWritten.getEncryptionVersion()).thenReturn((short) 1);
        when(dualWritten.getSecretCiphertext()).thenReturn(envelope.ciphertext());
        when(dualWritten.getSecretNonce()).thenReturn(envelope.nonce());
        when(dualWritten.getWrappedDataKey()).thenReturn(envelope.wrappedDataKey());
        when(dualWritten.getWrappingKeyId()).thenReturn(envelope.wrappingKeyId());
        WebhookSecretMigrationBatch migration = new WebhookSecretMigrationBatch(
                repository, cipher, metrics, 100, "protected");

        assertEquals(1, migration.migrateNextBatch());
        assertEquals(1, migration.purgeNextLegacyBatch());

        verify(legacy).protect(envelope, true);
        verify(dualWritten).clearLegacySecret();
        verify(repository, times(2)).flush();
    }

    @Test
    void dualWriteModeEncryptsWithoutPurgingCompatibilityPlaintext() {
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        WebhookSecretCipher cipher = mock(WebhookSecretCipher.class);
        WebhookEndpoint legacy = legacyEndpoint();
        WebhookSecretCipher.Envelope envelope = envelope();
        when(repository.lockLegacyBatch(any(Pageable.class))).thenReturn(List.of(legacy));
        when(cipher.encrypt(any(), any(), any())).thenReturn(envelope);
        when(cipher.decrypt(any(), any(), anyShort(), any(), any(), any(), anyString()))
                .thenAnswer(invocation -> new byte[32]);
        WebhookSecretMigrationBatch migration = new WebhookSecretMigrationBatch(
                repository, cipher, mock(SluiceMetrics.class), 100, "dual-write");

        assertEquals(1, migration.migrateNextBatch());
        assertEquals(0, migration.purgeNextLegacyBatch());

        verify(legacy).protect(envelope, false);
        verify(repository, never()).lockProtectedBatchWithLegacySecret(any(Pageable.class));
    }

    @Test
    void verificationFailurePreservesTheLegacySecret() {
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        WebhookSecretCipher cipher = mock(WebhookSecretCipher.class);
        WebhookEndpoint legacy = legacyEndpoint();
        when(repository.lockLegacyBatch(any(Pageable.class))).thenReturn(List.of(legacy));
        when(cipher.encrypt(any(), any(), any())).thenReturn(envelope());
        when(cipher.decrypt(any(), any(), anyShort(), any(), any(), any(), anyString()))
                .thenReturn(new byte[]{1});
        WebhookSecretMigrationBatch migration = new WebhookSecretMigrationBatch(
                repository, cipher, mock(SluiceMetrics.class), 100, "protected");

        assertThrows(IllegalStateException.class, migration::migrateNextBatch);

        verify(legacy, never()).protect(any(), anyBoolean());
        verify(repository, never()).flush();
    }

    @Test
    void cutoverFailureDoesNotEraseTheCompatibilitySecret() {
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        WebhookSecretCipher cipher = mock(WebhookSecretCipher.class);
        WebhookEndpoint endpoint = legacyEndpoint();
        WebhookSecretCipher.Envelope envelope = envelope();
        when(endpoint.getEncryptionVersion()).thenReturn((short) 1);
        when(endpoint.getSecretCiphertext()).thenReturn(envelope.ciphertext());
        when(endpoint.getSecretNonce()).thenReturn(envelope.nonce());
        when(endpoint.getWrappedDataKey()).thenReturn(envelope.wrappedDataKey());
        when(endpoint.getWrappingKeyId()).thenReturn(envelope.wrappingKeyId());
        when(repository.lockProtectedBatchWithLegacySecret(any(Pageable.class))).thenReturn(List.of(endpoint));
        when(cipher.decrypt(any(), any(), anyShort(), any(), any(), any(), anyString()))
                .thenThrow(new com.sluice.api.webhook.crypto.WebhookSecretException("Key unavailable"));
        WebhookSecretMigrationBatch migration = new WebhookSecretMigrationBatch(
                repository, cipher, mock(SluiceMetrics.class), 100, "protected");

        assertThrows(com.sluice.api.webhook.crypto.WebhookSecretException.class, migration::purgeNextLegacyBatch);

        verify(endpoint, never()).clearLegacySecret();
        verify(repository, never()).flush();
    }

    private static WebhookEndpoint legacyEndpoint() {
        WebhookEndpoint endpoint = mock(WebhookEndpoint.class);
        when(endpoint.getProjectId()).thenReturn(UUID.randomUUID());
        when(endpoint.getId()).thenReturn(UUID.randomUUID());
        when(endpoint.getLegacySecretForMigration()).thenReturn(
                Base64.getEncoder().encodeToString(new byte[32]));
        return endpoint;
    }

    private static WebhookSecretCipher.Envelope envelope() {
        return new WebhookSecretCipher.Envelope((short) 1, new byte[48], new byte[12], new byte[48], "v1");
    }
}

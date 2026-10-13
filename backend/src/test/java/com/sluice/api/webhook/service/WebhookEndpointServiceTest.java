package com.sluice.api.webhook.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.webhook.crypto.LocalDataKeyWrapper;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookEndpointServiceTest {
    @Test
    void createsAnEncryptedEndpointAndRevealsTheSecretOnce() {
        UUID projectId = UUID.randomUUID();
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        when(repository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WebhookTargetValidator targets = mock(WebhookTargetValidator.class);
        when(targets.validate("https://hooks.example.test/run"))
                .thenReturn(URI.create("https://hooks.example.test/run"));
        byte[] wrappingKey = new byte[32];
        WebhookSecretCipher cipher = new WebhookSecretCipher(
                new LocalDataKeyWrapper("v1", Map.of("v1", wrappingKey)));
        WebhookEndpointService service = new WebhookEndpointService(repository, targets, cipher, "protected");

        WebhookEndpointService.CreatedEndpoint created = service.create("https://hooks.example.test/run",
                new ProjectContext(projectId, UUID.randomUUID(), false));

        assertEquals(32, Base64.getDecoder().decode(created.secret()).length);
        assertTrue(created.endpoint().hasProtectedSecret());
        assertEquals("", created.endpoint().getLegacySecretForMigration());
        assertArrayEquals(Base64.getDecoder().decode(created.secret()), cipher.decrypt(projectId,
                created.endpoint().getId(), created.endpoint().getEncryptionVersion(),
                created.endpoint().getSecretCiphertext(), created.endpoint().getSecretNonce(),
                created.endpoint().getWrappedDataKey(), created.endpoint().getWrappingKeyId()));
    }

    @Test
    void dualWriteModeKeepsACompatibilityValueForAStagedRollout() {
        UUID projectId = UUID.randomUUID();
        WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
        when(repository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WebhookTargetValidator targets = mock(WebhookTargetValidator.class);
        when(targets.validate(anyString())).thenReturn(URI.create("https://hooks.example.test/run"));
        WebhookSecretCipher cipher = new WebhookSecretCipher(
                new LocalDataKeyWrapper("v1", Map.of("v1", new byte[32])));
        WebhookEndpointService service = new WebhookEndpointService(repository, targets, cipher, "dual-write");

        WebhookEndpointService.CreatedEndpoint created = service.create("https://hooks.example.test/run",
                new ProjectContext(projectId, UUID.randomUUID(), false));

        assertTrue(created.endpoint().hasProtectedSecret());
        assertEquals(created.secret(), created.endpoint().getLegacySecretForMigration());
    }
}

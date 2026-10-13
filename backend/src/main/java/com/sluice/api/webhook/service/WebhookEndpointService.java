package com.sluice.api.webhook.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class WebhookEndpointService {
    private final WebhookEndpointRepository endpoints;
    private final WebhookTargetValidator targets;
    private final WebhookSecretCipher secrets;
    private final WebhookSecretMigrationMode migrationMode;
    private final SecureRandom random = new SecureRandom();

    public WebhookEndpointService(WebhookEndpointRepository endpoints, WebhookTargetValidator targets,
                                  WebhookSecretCipher secrets,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${sluice.webhooks.encryption.migration-mode:protected}") String migrationMode) {
        this.endpoints = endpoints; this.targets = targets; this.secrets = secrets;
        this.migrationMode = WebhookSecretMigrationMode.parse(migrationMode);
    }

    @Transactional
    public CreatedEndpoint create(String callbackUrl, ProjectContext context) {
        String normalized = targets.validate(callbackUrl).toASCIIString();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        UUID endpointId = UUID.randomUUID();
        try {
            String secret = Base64.getEncoder().encodeToString(bytes);
            var envelope = secrets.encrypt(context.getProjectId(), endpointId, bytes);
            WebhookEndpoint endpoint = endpoints.save(new WebhookEndpoint(endpointId, context.getProjectId(),
                    normalized, envelope, migrationMode.retainsLegacyPlaintext() ? secret : "", Instant.now()));
            return new CreatedEndpoint(endpoint, secret);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint require(UUID id, UUID projectId) {
        return endpoints.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook endpoint not found"));
    }

    public record CreatedEndpoint(WebhookEndpoint endpoint, String secret) {}
}

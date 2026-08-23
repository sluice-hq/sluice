package com.sluice.api.webhook.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class WebhookEndpointService {
    private final WebhookEndpointRepository endpoints;
    private final WebhookTargetValidator targets;
    private final SecureRandom random = new SecureRandom();

    public WebhookEndpointService(WebhookEndpointRepository endpoints, WebhookTargetValidator targets) {
        this.endpoints = endpoints; this.targets = targets;
    }

    @Transactional
    public CreatedEndpoint create(String callbackUrl, ProjectContext context) {
        String normalized = targets.validate(callbackUrl).toASCIIString();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        WebhookEndpoint endpoint = endpoints.save(new WebhookEndpoint(UUID.randomUUID(), context.getProjectId(),
                normalized, secret, Instant.now()));
        return new CreatedEndpoint(endpoint, secret);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint require(UUID id, UUID projectId) {
        return endpoints.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook endpoint not found"));
    }

    public record CreatedEndpoint(WebhookEndpoint endpoint, String secret) {}
}

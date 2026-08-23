package com.sluice.api.webhook.dto;

import java.time.Instant;
import java.util.UUID;

public record WebhookEndpointResponse(UUID id, String callbackUrl, String secret, Instant createdAt) {}

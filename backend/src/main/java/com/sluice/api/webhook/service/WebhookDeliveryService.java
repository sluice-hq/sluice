package com.sluice.api.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.crypto.WebhookSecretException;
import com.sluice.api.webhook.domain.*;
import com.sluice.api.webhook.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class WebhookDeliveryService {
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;
    private final WebhookEndpointRepository endpoints;
    private final WebhookTargetValidator targets;
    private final WebhookSigner signer;
    private final WebhookSecretCipher secrets;
    private final WebhookSender sender;
    private final ObjectMapper objectMapper;
    private final SluiceMetrics metrics;
    private final int batchSize;
    private final int maxAttempts;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveries,
                                  WebhookDeliveryAttemptRepository attempts,
                                  WebhookEndpointRepository endpoints, WebhookTargetValidator targets,
                                  WebhookSigner signer, WebhookSecretCipher secrets,
                                  WebhookSender sender, ObjectMapper objectMapper,
                                  SluiceMetrics metrics,
                                  @org.springframework.beans.factory.annotation.Value("${sluice.webhooks.batch-size:20}") int batchSize,
                                  @org.springframework.beans.factory.annotation.Value("${sluice.webhooks.max-attempts:5}") int maxAttempts) {
        this.deliveries=deliveries; this.attempts=attempts; this.endpoints=endpoints; this.targets=targets;
        this.signer=signer; this.secrets=secrets; this.sender=sender; this.objectMapper=objectMapper;
        this.metrics=metrics;
        this.batchSize=Math.max(1, Math.min(100, batchSize)); this.maxAttempts=Math.max(1, Math.min(10, maxAttempts));
    }

    @Transactional
    public void enqueue(OutboxEvent event) throws Exception {
        if (deliveries.existsByEventId(event.getId())) return;
        JsonNode payload = objectMapper.readTree(event.getPayload());
        UUID endpointId = UUID.fromString(payload.path("webhookEndpointId").asText());
        WebhookEndpoint endpoint = endpoints.findByIdAndProjectId(endpointId, event.getProjectId())
                .orElseThrow(() -> new IllegalStateException("Webhook endpoint no longer exists"));
        deliveries.save(new WebhookDelivery(UUID.randomUUID(), event.getId(), endpoint.getId(), event.getProjectId(),
                event.getAggregateId(), event.getEventType(), event.getPayload(), Instant.now()));
    }

    @Transactional
    public int deliverBatch() {
        List<WebhookDelivery> batch = deliveries.lockNextBatch(batchSize);
        for (WebhookDelivery delivery : batch) deliver(delivery);
        return batch.size();
    }

    private void deliver(WebhookDelivery delivery) {
        int number = delivery.beginAttempt();
        Instant started = Instant.now();
        Integer response = null;
        String outcome;
        String error = null;
        try {
            WebhookEndpoint endpoint = endpoints.findByIdAndProjectId(delivery.getEndpointId(), delivery.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("Webhook endpoint no longer exists"));
            URI target = targets.validate(endpoint.getCallbackUrl());
            long timestamp = Instant.now().getEpochSecond();
            byte[] signingSecret = signingSecret(endpoint);
            Map<String, String> headers;
            try {
                headers = Map.of(
                        "X-Sluice-Event-Id", delivery.getEventId().toString(),
                        "X-Sluice-Timestamp", Long.toString(timestamp),
                        "X-Sluice-Signature", signer.sign(signingSecret, timestamp, delivery.getPayload()));
            } finally {
                Arrays.fill(signingSecret, (byte) 0);
            }
            response = sender.send(target, delivery.getPayload(), headers);
            if (response >= 200 && response < 300) {
                delivery.delivered(); outcome = "DELIVERED";
            } else if (isTransient(response) && number < maxAttempts) {
                error = "transient_http_status"; delivery.retry(error); outcome = "RETRY";
            } else {
                error = "webhook_rejected"; delivery.fail(error); outcome = "FAILED";
            }
        } catch (Exception exception) {
            error = exception instanceof IllegalArgumentException ? "unsafe_webhook_target"
                    : exception instanceof WebhookSecretException ? "webhook_secret_unavailable"
                    : "webhook_transport_error";
            if (!(exception instanceof IllegalArgumentException) && number < maxAttempts) {
                delivery.retry(error); outcome = "RETRY";
            } else {
                delivery.fail(error); outcome = "FAILED";
            }
        }
        deliveries.save(delivery);
        attempts.save(new WebhookDeliveryAttempt(UUID.randomUUID(), delivery.getId(), number, started,
                Instant.now(), response, outcome, error));
        metrics.webhookDelivery(outcome.toLowerCase(java.util.Locale.ROOT), response,
                java.time.Duration.between(started, Instant.now()).toNanos());
    }

    private boolean isTransient(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private byte[] signingSecret(WebhookEndpoint endpoint) {
        if (endpoint.hasProtectedSecret()) {
            return secrets.decrypt(endpoint.getProjectId(), endpoint.getId(), endpoint.getEncryptionVersion(),
                    endpoint.getSecretCiphertext(), endpoint.getSecretNonce(), endpoint.getWrappedDataKey(),
                    endpoint.getWrappingKeyId());
        }
        try {
            byte[] legacy = java.util.Base64.getDecoder().decode(endpoint.getLegacySecretForMigration());
            if (legacy.length != 32) throw new IllegalArgumentException("Unexpected legacy secret length");
            return legacy;
        } catch (IllegalArgumentException exception) {
            throw new WebhookSecretException("Webhook signing secret is unavailable", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> list(UUID endpointId, UUID projectId) {
        return deliveries.findByEndpointIdAndProjectIdOrderByCreatedAtDesc(endpointId, projectId);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryAttempt> attempts(UUID deliveryId) {
        return attempts.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId);
    }
}

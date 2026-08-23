package com.sluice.api.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.webhook.domain.*;
import com.sluice.api.webhook.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookDeliveryService {
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;
    private final WebhookEndpointRepository endpoints;
    private final WebhookTargetValidator targets;
    private final WebhookSigner signer;
    private final WebhookSender sender;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveries,
                                  WebhookDeliveryAttemptRepository attempts,
                                  WebhookEndpointRepository endpoints, WebhookTargetValidator targets,
                                  WebhookSigner signer, WebhookSender sender, ObjectMapper objectMapper,
                                  @org.springframework.beans.factory.annotation.Value("${sluice.webhooks.batch-size:20}") int batchSize,
                                  @org.springframework.beans.factory.annotation.Value("${sluice.webhooks.max-attempts:5}") int maxAttempts) {
        this.deliveries=deliveries; this.attempts=attempts; this.endpoints=endpoints; this.targets=targets;
        this.signer=signer; this.sender=sender; this.objectMapper=objectMapper;
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
            Map<String, String> headers = Map.of(
                    "X-Sluice-Event-Id", delivery.getEventId().toString(),
                    "X-Sluice-Timestamp", Long.toString(timestamp),
                    "X-Sluice-Signature", signer.sign(endpoint.getSecretValue(), timestamp, delivery.getPayload()));
            response = sender.send(target, delivery.getPayload(), headers);
            if (response >= 200 && response < 300) {
                delivery.delivered(); outcome = "DELIVERED";
            } else if (isTransient(response) && number < maxAttempts) {
                error = "transient_http_status"; delivery.retry(error); outcome = "RETRY";
            } else {
                error = "webhook_rejected"; delivery.fail(error); outcome = "FAILED";
            }
        } catch (Exception exception) {
            error = exception instanceof IllegalArgumentException ? "unsafe_webhook_target" : "webhook_transport_error";
            if (!(exception instanceof IllegalArgumentException) && number < maxAttempts) {
                delivery.retry(error); outcome = "RETRY";
            } else {
                delivery.fail(error); outcome = "FAILED";
            }
        }
        deliveries.save(delivery);
        attempts.save(new WebhookDeliveryAttempt(UUID.randomUUID(), delivery.getId(), number, started,
                Instant.now(), response, outcome, error));
    }

    private boolean isTransient(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
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

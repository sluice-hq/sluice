package com.sluice.api.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.webhook.crypto.LocalDataKeyWrapper;
import com.sluice.api.webhook.crypto.WebhookSecretCipher;
import com.sluice.api.webhook.domain.WebhookDelivery;
import com.sluice.api.webhook.domain.WebhookDeliveryAttempt;
import com.sluice.api.webhook.domain.WebhookEndpoint;
import com.sluice.api.webhook.repository.WebhookDeliveryAttemptRepository;
import com.sluice.api.webhook.repository.WebhookDeliveryRepository;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookDeliveryServiceTest {
    @Test
    void storesEveryAttemptAndRetriesOnlyWithinTheBound() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(UUID.randomUUID(), UUID.randomUUID(), endpointId,
                projectId, UUID.randomUUID(), "run.completed", "{\"status\":\"COMPLETED\"}", Instant.now());
        WebhookSecretCipher secretCipher = cipher();
        WebhookEndpoint endpoint = new WebhookEndpoint(endpointId, projectId, "https://hooks.example.test/run",
                secretCipher.encrypt(projectId, endpointId, "secret".getBytes(StandardCharsets.UTF_8)), Instant.now());
        WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
        WebhookDeliveryAttemptRepository attempts = mock(WebhookDeliveryAttemptRepository.class);
        WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
        WebhookSender sender = mock(WebhookSender.class);
        when(deliveries.lockNextBatch(5)).thenReturn(List.of(delivery));
        when(endpoints.findByIdAndProjectId(endpointId, projectId)).thenReturn(java.util.Optional.of(endpoint));
        when(sender.send(any(), anyString(), anyMap())).thenReturn(503, 204);
        WebhookTargetValidator targets = new WebhookTargetValidator(
                ignored -> new InetAddress[]{InetAddress.getByName("203.0.113.10")});
        WebhookDeliveryService service = new WebhookDeliveryService(deliveries, attempts, endpoints, targets,
                new WebhookSigner(), secretCipher, sender, new ObjectMapper(), mock(SluiceMetrics.class), 5, 3);

        service.deliverBatch();
        assertEquals("PENDING", delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        service.deliverBatch();
        assertEquals("DELIVERED", delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());
        verify(attempts, times(2)).save(any(WebhookDeliveryAttempt.class));
    }

    private static WebhookSecretCipher cipher() {
        byte[] wrappingKey = new byte[32];
        java.util.Arrays.fill(wrappingKey, (byte) 7);
        return new WebhookSecretCipher(new LocalDataKeyWrapper("v1", Map.of("v1", wrappingKey)));
    }
}

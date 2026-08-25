package com.sluice.api.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.webhook.domain.*;
import com.sluice.api.webhook.repository.*;
import com.sluice.api.observability.SluiceMetrics;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
        WebhookEndpoint endpoint = new WebhookEndpoint(endpointId, projectId, "https://hooks.example.test/run",
                Base64.getEncoder().encodeToString("secret".getBytes()), Instant.now());
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
                new WebhookSigner(), sender, new ObjectMapper(), mock(SluiceMetrics.class), 5, 3);

        service.deliverBatch();
        assertEquals("PENDING", delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        service.deliverBatch();
        assertEquals("DELIVERED", delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());
        verify(attempts, times(2)).save(any(WebhookDeliveryAttempt.class));
    }
}

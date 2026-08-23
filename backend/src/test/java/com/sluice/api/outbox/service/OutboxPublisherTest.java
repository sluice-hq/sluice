package com.sluice.api.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.messaging.RunQueuePublisher;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import com.sluice.api.webhook.service.WebhookDeliveryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test
    void brokerOutageLeavesEventPendingAndALaterPollRecovers() throws Exception {
        UUID projectId = UUID.randomUUID();
        JobMessage message = new JobMessage(UUID.randomUUID(), UUID.randomUUID());
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), projectId, "run.queued", "JOB",
                message.getJobId(), new ObjectMapper().writeValueAsString(message));
        OutboxEventRepository events = mock(OutboxEventRepository.class);
        RunQueuePublisher queue = mock(RunQueuePublisher.class);
        when(events.lockNextBatch(10)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker offline")).doNothing().when(queue).publish(any(JobMessage.class));
        OutboxPublisher publisher = new OutboxPublisher(events, queue, mock(WebhookDeliveryService.class),
                new ObjectMapper(), 10);

        assertEquals(1, publisher.publishBatch());
        assertEquals("PENDING", event.getStatus());
        assertEquals(1, event.getAttempts());
        assertEquals("outbox_dispatch_failed", event.getLastError());

        assertEquals(1, publisher.publishBatch());
        assertEquals("PUBLISHED", event.getStatus());
        assertEquals(2, event.getAttempts());
        verify(queue, times(2)).publish(any(JobMessage.class));
    }
}

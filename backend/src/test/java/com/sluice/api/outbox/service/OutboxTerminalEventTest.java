package com.sluice.api.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxTerminalEventTest {
    @Test
    void terminalCallbackTimestampSerializesWithTheActualPlainApplicationMapper() throws Exception {
        var job = mock(Job.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getProjectId()).thenReturn(UUID.randomUUID());
        when(job.getWebhookEndpointId()).thenReturn(UUID.randomUUID());
        when(job.getStatus()).thenReturn(JobStatus.COMPLETED);
        var events = mock(OutboxEventRepository.class);
        when(events.save(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));
        var mapper = new ObjectMapper();
        var outputId = UUID.randomUUID();
        var event = new OutboxService(events, mapper).createTerminalEvent(job, outputId);
        var body = mapper.readTree(event.getPayload());
        assertDoesNotThrow(() -> Instant.parse(body.path("timestamp").asText()));
        assertEquals(event.getId().toString(), body.path("eventId").asText());
        assertEquals(job.getId().toString(), body.path("runId").asText());
        assertEquals(outputId.toString(), body.path("outputs").get(0).path("assetId").asText());
        assertEquals("COMPLETED", body.path("status").asText());
    }
}

package com.sluice.api.messaging;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.worker.JobWorker;
import com.sluice.api.worker.PermanentProcessingException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureServiceBusJobListenerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completesOnlyAfterTheWorkerFinishes() throws Exception {
        Fixture fixture = fixture(1);

        fixture.listener.receive(fixture.context);

        verify(fixture.worker).processJob(any(JobMessage.class));
        verify(fixture.context).complete();
        verify(fixture.context, never()).abandon();
        verify(fixture.metrics).queueConsume("completed");
    }

    @Test
    void completionFailureDoesNotReclassifySuccessfulProcessingAsAWorkerFailure() throws Exception {
        Fixture fixture = fixture(5);
        IllegalStateException settlementFailure = new IllegalStateException("message lock was lost");
        doThrow(settlementFailure).when(fixture.context).complete();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> fixture.listener.receive(fixture.context));

        assertEquals(settlementFailure, thrown);
        verify(fixture.worker).processJob(any(JobMessage.class));
        verify(fixture.context, never()).abandon();
        verify(fixture.context, never()).deadLetter(any(DeadLetterOptions.class));
        verify(fixture.jobs, never()).failJobSystem(any(), any(), any());
        verify(fixture.metrics).queueConsume("settlement_failed");
        verify(fixture.metrics, never()).queueConsume("completed");
    }

    @Test
    void abandonsTransientFailuresBeforeTheDeliveryLimit() throws Exception {
        Fixture fixture = fixture(2);
        doThrow(new IOException("database unavailable")).when(fixture.worker).processJob(any());

        fixture.listener.receive(fixture.context);

        verify(fixture.context).abandon();
        verify(fixture.context, never()).deadLetter(any(DeadLetterOptions.class));
        verify(fixture.jobs, never()).failJobSystem(any(), any(), any());
        verify(fixture.metrics).queueConsume("abandoned");
    }

    @Test
    void deadLettersPermanentFailuresAndMarksTheRunFailed() throws Exception {
        Fixture fixture = fixture(1);
        doThrow(new PermanentProcessingException("queue_asset_mismatch", "mismatch"))
                .when(fixture.worker).processJob(any());

        fixture.listener.receive(fixture.context);

        verify(fixture.jobs).failJobSystem(fixture.jobId, "broker_message_invalid",
                "Queue delivery could not be completed");
        ArgumentCaptor<DeadLetterOptions> options = ArgumentCaptor.forClass(DeadLetterOptions.class);
        verify(fixture.context).deadLetter(options.capture());
        assertEquals("PermanentProcessingFailure", options.getValue().getDeadLetterReason());
        verify(fixture.metrics).queueConsume("dead_lettered");
    }

    @Test
    void deadLettersTransientFailuresAtTheConfiguredDeliveryLimit() throws Exception {
        Fixture fixture = fixture(5);
        doThrow(new IOException("database unavailable")).when(fixture.worker).processJob(any());

        fixture.listener.receive(fixture.context);

        verify(fixture.jobs).failJobSystem(fixture.jobId, "broker_retry_exhausted",
                "Queue delivery could not be completed");
        verify(fixture.context).deadLetter(any(DeadLetterOptions.class));
        verify(fixture.context, never()).abandon();
    }

    private Fixture fixture(long deliveryCount) throws Exception {
        UUID jobId = UUID.randomUUID();
        JobMessage message = new JobMessage(jobId, UUID.randomUUID(), "request-123");
        ServiceBusReceivedMessage received = mock(ServiceBusReceivedMessage.class);
        when(received.getBody()).thenReturn(BinaryData.fromString(objectMapper.writeValueAsString(message)));
        when(received.getDeliveryCount()).thenReturn(deliveryCount);
        ServiceBusReceivedMessageContext context = mock(ServiceBusReceivedMessageContext.class);
        when(context.getMessage()).thenReturn(received);
        JobWorker worker = mock(JobWorker.class);
        JobService jobs = mock(JobService.class);
        SluiceMetrics metrics = mock(SluiceMetrics.class);
        AzureServiceBusJobListener listener = new AzureServiceBusJobListener(
                worker, jobs, objectMapper, metrics, 5);
        return new Fixture(jobId, context, worker, jobs, metrics, listener);
    }

    private record Fixture(UUID jobId, ServiceBusReceivedMessageContext context, JobWorker worker,
                           JobService jobs, SluiceMetrics metrics, AzureServiceBusJobListener listener) {
    }
}

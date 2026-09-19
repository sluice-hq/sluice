package com.sluice.api.messaging;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AzureServiceBusRunQueuePublisherTest {

    @Test
    void publishesJsonWithStableDuplicateDetectionAndCorrelationIdentifiers() throws Exception {
        ServiceBusSenderClient sender = mock(ServiceBusSenderClient.class);
        SluiceMetrics metrics = mock(SluiceMetrics.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UUID jobId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        JobMessage message = new JobMessage(jobId, assetId, "request-123");

        new AzureServiceBusRunQueuePublisher(sender, objectMapper, metrics).publish(deliveryId, message);

        ArgumentCaptor<ServiceBusMessage> published = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(sender).sendMessage(published.capture());
        ServiceBusMessage brokerMessage = published.getValue();
        assertEquals(deliveryId.toString(), brokerMessage.getMessageId());
        assertEquals("request-123", brokerMessage.getCorrelationId());
        assertEquals("run.queued", brokerMessage.getSubject());
        assertEquals("application/json", brokerMessage.getContentType());
        JobMessage decoded = objectMapper.readValue(brokerMessage.getBody().toString(), JobMessage.class);
        assertEquals(jobId, decoded.getJobId());
        assertEquals(assetId, decoded.getAssetId());
        verify(metrics).queuePublish("confirmed");
    }
}

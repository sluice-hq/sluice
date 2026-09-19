package com.sluice.api.messaging;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnApiRuntime
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "servicebus")
public class AzureServiceBusRunQueuePublisher implements RunQueuePublisher {
    private final ServiceBusSenderClient sender;
    private final ObjectMapper objectMapper;
    private final SluiceMetrics metrics;

    public AzureServiceBusRunQueuePublisher(ServiceBusSenderClient sender, ObjectMapper objectMapper,
                                             SluiceMetrics metrics) {
        this.sender = sender;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    public void publish(UUID deliveryId, JobMessage message) {
        try {
            ServiceBusMessage serviceBusMessage = new ServiceBusMessage(
                    BinaryData.fromString(objectMapper.writeValueAsString(message)))
                    .setMessageId(deliveryId.toString())
                    .setSubject("run.queued")
                    .setContentType("application/json");
            if (message.getRequestId() != null && !message.getRequestId().isBlank()) {
                serviceBusMessage.setCorrelationId(message.getRequestId());
            }
            sender.sendMessage(serviceBusMessage);
            metrics.queuePublish("confirmed");
        } catch (JsonProcessingException exception) {
            metrics.queuePublish("failed");
            throw new IllegalArgumentException("Could not serialize the run queue message", exception);
        } catch (RuntimeException exception) {
            metrics.queuePublish("failed");
            throw exception;
        }
    }
}

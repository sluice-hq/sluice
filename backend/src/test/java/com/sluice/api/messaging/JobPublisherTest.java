package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobPublisherTest {

    @Test
    void returnsOnlyAfterBrokerAcknowledgesThePublish() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY), any(JobMessage.class), any(CorrelationData.class));
        JobMessage message = new JobMessage(UUID.randomUUID(), UUID.randomUUID());
        SluiceMetrics metrics = mock(SluiceMetrics.class);

        assertDoesNotThrow(() -> new JobPublisher(template, Duration.ofSeconds(1), metrics).publish(message));

        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY), eq(message), correlation.capture());
        verify(metrics).queuePublish("confirmed");
    }

    @Test
    void brokerNackIsSurfacedToTheOutbox() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected publish"));
            return null;
        }).when(template).convertAndSend(eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY), any(JobMessage.class), any(CorrelationData.class));

        SluiceMetrics metrics = mock(SluiceMetrics.class);
        assertThrows(AmqpException.class, () -> new JobPublisher(template, Duration.ofSeconds(1), metrics)
                .publish(new JobMessage(UUID.randomUUID(), UUID.randomUUID())));
        verify(metrics).queuePublish("failed");
    }
}

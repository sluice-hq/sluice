package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@com.sluice.api.runtime.ConditionalOnApiRuntime
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "sluice.messaging.provider", havingValue = "rabbit", matchIfMissing = true)
public class JobPublisher implements RunQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;
    private final SluiceMetrics metrics;

    public JobPublisher(RabbitTemplate rabbitTemplate,
                        @Value("${sluice.outbox.publisher-confirm-timeout:5s}") Duration confirmTimeout,
                        SluiceMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
        this.metrics = metrics;
    }

    public void publishJob(UUID deliveryId, JobMessage message) {
        try {
            publishAndConfirm(deliveryId, message);
            metrics.queuePublish("confirmed");
        } catch (RuntimeException exception) {
            metrics.queuePublish("failed");
            throw exception;
        }
    }

    private void publishAndConfirm(UUID deliveryId, JobMessage message) {
        CorrelationData correlation = new CorrelationData(deliveryId.toString());
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.ROUTING_KEY, message, correlation);

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpTimeoutException("Timed out waiting for RabbitMQ publisher confirmation", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ publisher confirmation", exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("RabbitMQ publisher confirmation failed", exception.getCause());
        }

        if (!confirm.ack()) {
            String reason = confirm.reason() == null ? "no reason supplied" : confirm.reason();
            throw new AmqpException("RabbitMQ negatively acknowledged the publish: " + reason);
        }
    }

    @Override
    public void publish(UUID deliveryId, JobMessage message) {
        publishJob(deliveryId, message);
    }
}

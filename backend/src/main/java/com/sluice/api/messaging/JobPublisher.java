package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class JobPublisher implements RunQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public JobPublisher(RabbitTemplate rabbitTemplate,
                        @Value("${sluice.outbox.publisher-confirm-timeout:5s}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public void publishJob(JobMessage message) {
        CorrelationData correlation = new CorrelationData(message.getJobId().toString());
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
    public void publish(JobMessage message) {
        publishJob(message);
    }
}

package com.sluice.api.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String QUEUE_NAME = "asset.processing.queue";
    public static final String EXCHANGE_NAME = "asset.exchange";
    public static final String ROUTING_KEY = "asset.processing.key";
    
    public static final String DLQ_NAME = "asset.processing.dlq";
    public static final String DLX_NAME = "asset.dlx";
    public static final String DLQ_ROUTING_KEY = "asset.processing.dlq.key";

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public DirectExchange dlx() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Binding dlqBinding(Queue dlq, DirectExchange dlx) {
        return BindingBuilder.bind(dlq).to(dlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public org.springframework.amqp.rabbit.retry.MessageRecoverer messageRecoverer(
            com.sluice.api.job.service.JobService jobService) {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return (message, cause) -> {
            try {
                com.sluice.api.messaging.dto.JobMessage jobMsg = objectMapper.readValue(message.getBody(), com.sluice.api.messaging.dto.JobMessage.class);
                jobService.updateJobStatus(jobMsg.getJobId(), com.sluice.api.job.domain.JobStatus.FAILED);
            } catch (Exception e) {
                System.err.println("Failed to parse message in recoverer: " + e.getMessage());
            }
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(cause);
        };
    }
}

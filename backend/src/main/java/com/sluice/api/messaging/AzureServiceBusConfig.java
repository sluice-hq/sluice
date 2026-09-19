package com.sluice.api.messaging;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.runtime.ConditionalOnWorkerRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "servicebus")
public class AzureServiceBusConfig {

    @Bean
    TokenCredential serviceBusCredential(
            @Value("${sluice.messaging.service-bus.managed-identity-client-id:}") String clientId) {
        DefaultAzureCredentialBuilder builder = new DefaultAzureCredentialBuilder();
        if (clientId != null && !clientId.isBlank()) {
            builder.managedIdentityClientId(clientId.trim());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnApiRuntime
    ServiceBusSenderClient serviceBusSenderClient(
            TokenCredential credential,
            @Value("${sluice.messaging.service-bus.fully-qualified-namespace}") String namespace,
            @Value("${sluice.messaging.service-bus.queue-name}") String queueName) {
        requireConfigured(namespace, "Azure Service Bus fully qualified namespace");
        requireConfigured(queueName, "Azure Service Bus queue name");
        return new ServiceBusClientBuilder()
                .credential(namespace.trim(), credential)
                .sender()
                .queueName(queueName.trim())
                .buildClient();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnWorkerRuntime
    ServiceBusProcessorClient serviceBusProcessorClient(
            TokenCredential credential,
            AzureServiceBusJobListener listener,
            @Value("${sluice.messaging.service-bus.fully-qualified-namespace}") String namespace,
            @Value("${sluice.messaging.service-bus.queue-name}") String queueName,
            @Value("${sluice.messaging.service-bus.max-concurrent-calls:2}") int maxConcurrentCalls,
            @Value("${sluice.messaging.service-bus.prefetch-count:0}") int prefetchCount,
            @Value("${sluice.messaging.service-bus.max-auto-lock-renew-duration:PT15M}")
            Duration maxAutoLockRenewDuration) {
        requireConfigured(namespace, "Azure Service Bus fully qualified namespace");
        requireConfigured(queueName, "Azure Service Bus queue name");
        return new ServiceBusClientBuilder()
                .credential(namespace.trim(), credential)
                .processor()
                .queueName(queueName.trim())
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .disableAutoComplete()
                .maxConcurrentCalls(Math.max(1, Math.min(maxConcurrentCalls, 16)))
                .prefetchCount(Math.max(0, Math.min(prefetchCount, 100)))
                .maxAutoLockRenewDuration(maxAutoLockRenewDuration)
                .processMessage(listener::receive)
                .processError(listener::onError)
                .buildProcessorClient();
    }

    private static void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured");
        }
    }
}

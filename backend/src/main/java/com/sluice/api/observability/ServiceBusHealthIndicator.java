package com.sluice.api.observability;

import com.sluice.api.messaging.AzureServiceBusRunQueuePublisher;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Passive last-operation evidence; does not require Service Bus management permissions or paid probes. */
@Component("serviceBus")
@ConditionalOnApiRuntime
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "servicebus")
public class ServiceBusHealthIndicator implements HealthIndicator {
    private final AzureServiceBusRunQueuePublisher publisher;

    public ServiceBusHealthIndicator(AzureServiceBusRunQueuePublisher publisher) { this.publisher = publisher; }

    @Override
    public Health health() {
        Boolean success = publisher.lastPublishSucceeded();
        var builder = success == null ? Health.unknown() : success ? Health.up() : Health.down();
        return builder.withDetail("evidence", "Last API queue publish; unknown until first operation, not an active connectivity probe").build();
    }
}

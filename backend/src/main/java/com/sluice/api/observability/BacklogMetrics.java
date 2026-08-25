package com.sluice.api.observability;

import com.sluice.api.outbox.repository.OutboxEventRepository;
import com.sluice.api.webhook.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Database-backed gauges for durable work that has not yet been dispatched. */
@Component
public class BacklogMetrics {

    public BacklogMetrics(MeterRegistry registry, OutboxEventRepository outbox,
                          WebhookDeliveryRepository webhooks) {
        Gauge.builder("sluice.outbox.pending", outbox, repository -> repository.countByStatus("PENDING"))
                .description("Outbox events waiting for dispatch")
                .register(registry);
        Gauge.builder("sluice.webhooks.pending", webhooks, repository -> repository.countByStatus("PENDING"))
                .description("Webhook deliveries waiting for an attempt")
                .register(registry);
    }
}

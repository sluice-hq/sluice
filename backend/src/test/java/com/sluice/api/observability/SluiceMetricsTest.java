package com.sluice.api.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SluiceMetricsTest {

    @Test
    void exposesOperationalOutcomesWithoutHighCardinalityIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SluiceMetrics metrics = new SluiceMetrics(registry);

        metrics.queuePublish("confirmed");
        metrics.outboxDispatch("run.queued", "published");
        metrics.webhookDelivery("delivered", 204, 1_000_000);
        metrics.storage("upload", "success", 2_000_000);
        metrics.dependencyHealth("rabbit", true);

        assertEquals(1, registry.get("sluice.queue.publishes").tag("outcome", "confirmed").counter().count());
        assertEquals(1, registry.get("sluice.outbox.dispatches").tag("event_type", "run.queued")
                .tag("outcome", "published").counter().count());
        assertEquals(1, registry.get("sluice.webhook.delivery").tag("outcome", "delivered")
                .tag("status_class", "2xx").timer().count());
        assertEquals(1, registry.get("sluice.storage.operation").tag("operation", "upload")
                .tag("outcome", "success").timer().count());
        assertEquals(1, registry.get("sluice.dependency.health").tag("dependency", "rabbit").gauge().value());
    }
}

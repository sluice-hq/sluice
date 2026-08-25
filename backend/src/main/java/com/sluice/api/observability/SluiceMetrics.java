package com.sluice.api.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality domain counters used by the local dashboard and Azure monitoring. */
@Component
public class SluiceMetrics {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> dependencyStates = new ConcurrentHashMap<>();

    public SluiceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void job(String status) {
        registry.counter("sluice.jobs", "status", status).increment();
    }

    public void step(String processor, String status) {
        registry.counter("sluice.steps", "processor", processor, "status", status).increment();
    }

    public void governance(String decision) {
        registry.counter("sluice.governance.decisions", "decision", decision).increment();
    }

    public void queuePublish(String outcome) {
        registry.counter("sluice.queue.publishes", "outcome", outcome).increment();
    }

    public void outboxDispatch(String eventType, String outcome) {
        registry.counter("sluice.outbox.dispatches", "event_type", eventType, "outcome", outcome).increment();
    }

    public void webhookDelivery(String outcome, Integer responseStatus, long elapsedNanos) {
        String statusClass = responseStatus == null ? "none" : responseStatus / 100 + "xx";
        Timer.builder("sluice.webhook.delivery")
                .tags("outcome", outcome, "status_class", statusClass)
                .register(registry)
                .record(Duration.ofNanos(elapsedNanos));
    }

    public void storage(String operation, String outcome, long elapsedNanos) {
        Timer.builder("sluice.storage.operation")
                .tags("operation", operation, "outcome", outcome)
                .register(registry)
                .record(Duration.ofNanos(elapsedNanos));
    }

    public void dependencyHealth(String dependency, boolean healthy) {
        AtomicInteger state = dependencyStates.computeIfAbsent(dependency, name -> {
            AtomicInteger value = new AtomicInteger();
            registry.gauge("sluice.dependency.health", java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("dependency", name)), value);
            return value;
        });
        state.set(healthy ? 1 : 0);
    }
}

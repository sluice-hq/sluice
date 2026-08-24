package com.sluice.api.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Low-cardinality domain counters used by the local dashboard and Azure monitoring. */
@Component
public class SluiceMetrics {
    private final MeterRegistry registry;

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
}

package com.sluice.api.observability;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes dependency gauges even when no user has the dashboard open. */
@Component
@ConditionalOnProperty(name = "sluice.observability.dependency-probe.enabled", matchIfMissing = true)
@ConditionalOnApiRuntime
public class DependencyHealthProbe {
    private final DependencyHealthService dependencies;

    public DependencyHealthProbe(DependencyHealthService dependencies) {
        this.dependencies = dependencies;
    }

    @Scheduled(initialDelayString = "${sluice.observability.dependency-probe.initial-delay-ms:1000}",
            fixedDelayString = "${sluice.observability.dependency-probe.interval-ms:30000}",
            scheduler = "dependencyHealthTaskScheduler")
    public void refresh() {
        dependencies.refresh();
    }
}

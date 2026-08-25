package com.sluice.api.observability;

import com.sluice.api.dashboard.dto.DashboardResponse.DependencyHealth;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DependencyHealthService {
    private final HealthEndpoint healthEndpoint;
    private final SluiceMetrics metrics;
    private final AtomicReference<List<DependencyHealth>> snapshot = new AtomicReference<>(List.of(
            new DependencyHealth("PostgreSQL", 0, "DEGRADED"),
            new DependencyHealth("RabbitMQ", 0, "DEGRADED"),
            new DependencyHealth("Blob Storage", 0, "DEGRADED")));

    public DependencyHealthService(HealthEndpoint healthEndpoint, SluiceMetrics metrics) {
        this.healthEndpoint = healthEndpoint;
        this.metrics = metrics;
    }

    public List<DependencyHealth> current() {
        return snapshot.get();
    }

    public void refresh() {
        snapshot.set(List.of(
                probe("PostgreSQL", "db"),
                probe("RabbitMQ", "rabbit"),
                probe("Blob Storage", "azureBlob")));
    }

    private DependencyHealth probe(String displayName, String component) {
        long started = System.nanoTime();
        String status = "DOWN";
        try {
            HealthDescriptor health = healthEndpoint.healthForPath(component);
            String code = health == null ? "UNKNOWN" : health.getStatus().getCode();
            status = switch (code) {
                case "UP" -> "HEALTHY";
                case "UNKNOWN" -> "DEGRADED";
                default -> "DOWN";
            };
        } catch (RuntimeException ignored) {
            // A missing or failing contributor is reported as down without leaking internals.
        }
        long latencyMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        metrics.dependencyHealth(component, "HEALTHY".equals(status));
        return new DependencyHealth(displayName, latencyMs, status);
    }
}

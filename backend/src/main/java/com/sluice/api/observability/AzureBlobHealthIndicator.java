package com.sluice.api.observability;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class AzureBlobHealthIndicator implements HealthIndicator {
    private final BlobServiceClient serviceClient;
    private final String containerName;

    public AzureBlobHealthIndicator(BlobServiceClient serviceClient,
                                    @Value("${azure.storage.container-name:assets}") String containerName) {
        this.serviceClient = serviceClient;
        this.containerName = containerName;
    }

    @Override
    public Health health() {
        try {
            boolean available = serviceClient.getBlobContainerClient(containerName).exists();
            return available
                    ? Health.up().withDetail("container", containerName).build()
                    : Health.down().withDetail("container", containerName).build();
        } catch (RuntimeException exception) {
            return Health.down(exception).build();
        }
    }
}

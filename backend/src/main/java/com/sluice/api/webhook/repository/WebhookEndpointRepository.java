package com.sluice.api.webhook.repository;

import com.sluice.api.webhook.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    Optional<WebhookEndpoint> findByIdAndProjectId(UUID id, UUID projectId);
}

package com.sluice.api.webhook.repository;

import com.sluice.api.webhook.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    boolean existsByEventId(UUID eventId);
    List<WebhookDelivery> findByEndpointIdAndProjectIdOrderByCreatedAtDesc(UUID endpointId, UUID projectId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM webhook_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> lockNextBatch(@org.springframework.data.repository.query.Param("batchSize") int batchSize);
}

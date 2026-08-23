package com.sluice.api.webhook.repository;

import com.sluice.api.webhook.domain.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {
    List<WebhookDeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}

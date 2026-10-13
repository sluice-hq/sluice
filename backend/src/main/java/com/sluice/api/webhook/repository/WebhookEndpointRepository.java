package com.sluice.api.webhook.repository;

import com.sluice.api.webhook.domain.WebhookEndpoint;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    Optional<WebhookEndpoint> findByIdAndProjectId(UUID id, UUID projectId);

    long countByEncryptionVersionIsNull();

    @Query("select count(endpoint) from WebhookEndpoint endpoint where endpoint.legacySecretValue <> ''")
    long countWithLegacySecret();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select endpoint from WebhookEndpoint endpoint where endpoint.encryptionVersion is null order by endpoint.createdAt, endpoint.id")
    List<WebhookEndpoint> lockLegacyBatch(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select endpoint from WebhookEndpoint endpoint where endpoint.encryptionVersion is not null and endpoint.legacySecretValue <> '' order by endpoint.createdAt, endpoint.id")
    List<WebhookEndpoint> lockProtectedBatchWithLegacySecret(Pageable pageable);
}

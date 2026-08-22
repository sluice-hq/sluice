package com.sluice.api.auth.repository;

import com.sluice.api.auth.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);
    List<ApiKey> findByProjectId(UUID projectId);
    List<ApiKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Modifying
    @Transactional
    @Query("update ApiKey key set key.lastUsedAt = :now where key.id = :id and (key.lastUsedAt is null or key.lastUsedAt < :cutoff)")
    int updateLastUsedAtIfStale(UUID id, Instant now, Instant cutoff);
}

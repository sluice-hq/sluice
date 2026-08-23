package com.sluice.api.idempotency.repository;

import com.sluice.api.idempotency.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByProjectIdAndOperationAndIdempotencyKey(
            UUID projectId, String operation, String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records
                (id, project_id, operation, idempotency_key, request_hash, resource_id)
            VALUES (:id, :projectId, :operation, :idempotencyKey, :requestHash, :resourceId)
            ON CONFLICT (project_id, operation, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("projectId") UUID projectId,
                       @Param("operation") String operation,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash,
                       @Param("resourceId") UUID resourceId);
}

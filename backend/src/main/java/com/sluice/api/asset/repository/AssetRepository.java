package com.sluice.api.asset.repository;

import com.sluice.api.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByIdAndProjectId(UUID id, UUID projectId);
    Page<Asset> findAllByProjectId(UUID projectId, Pageable pageable);
    long countByProjectId(UUID projectId);
    java.util.List<Asset> findByProducingJobIdAndProjectId(UUID producingJobId, UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO assets (
                id, filename, size, content_type, storage_url, upload_status, created_at,
                project_id, producing_job_id, parent_asset_id
            ) VALUES (
                :id, :filename, :size, :contentType, :storageUrl, :uploadStatus, :createdAt,
                :projectId, :producingJobId, :parentAssetId
            )
            ON CONFLICT (producing_job_id) DO UPDATE SET
                filename = EXCLUDED.filename,
                size = EXCLUDED.size,
                content_type = EXCLUDED.content_type,
                storage_url = EXCLUDED.storage_url,
                upload_status = EXCLUDED.upload_status,
                parent_asset_id = EXCLUDED.parent_asset_id
            """, nativeQuery = true)
    int upsertProducedOutput(@Param("id") UUID id,
                             @Param("filename") String filename,
                             @Param("size") long size,
                             @Param("contentType") String contentType,
                             @Param("storageUrl") String storageUrl,
                             @Param("uploadStatus") String uploadStatus,
                             @Param("createdAt") Instant createdAt,
                             @Param("projectId") UUID projectId,
                             @Param("producingJobId") UUID producingJobId,
                             @Param("parentAssetId") UUID parentAssetId);
}

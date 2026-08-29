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
    @Query("""
            SELECT asset FROM Asset asset
            WHERE asset.projectId = :projectId
              AND (:filterFilename = false OR LOCATE(:filename, LOWER(asset.filename)) > 0)
              AND (:filterStatus = false OR asset.uploadStatus = :status)
              AND (:filterMediaType = false OR LOCATE(CONCAT(:mediaType, '/'), LOWER(asset.contentType)) = 1)
              AND (:filterCreatedFrom = false OR asset.createdAt >= :createdFrom)
              AND (:filterCreatedBefore = false OR asset.createdAt < :createdBefore)
              AND (:filterExternalSubject = false OR asset.externalSubjectId = :externalSubjectId)
              AND (:filterExternalReference = false OR asset.externalReference = :externalReference)
            """)
    Page<Asset> searchAssets(
            @Param("projectId") UUID projectId,
            @Param("filterFilename") boolean filterFilename,
            @Param("filename") String filename,
            @Param("filterStatus") boolean filterStatus,
            @Param("status") Asset.UploadStatus status,
            @Param("filterMediaType") boolean filterMediaType,
            @Param("mediaType") String mediaType,
            @Param("filterCreatedFrom") boolean filterCreatedFrom,
            @Param("createdFrom") Instant createdFrom,
            @Param("filterCreatedBefore") boolean filterCreatedBefore,
            @Param("createdBefore") Instant createdBefore,
            @Param("filterExternalSubject") boolean filterExternalSubject,
            @Param("externalSubjectId") String externalSubjectId,
            @Param("filterExternalReference") boolean filterExternalReference,
            @Param("externalReference") String externalReference,
            Pageable pageable);
    long countByProjectId(UUID projectId);
    java.util.List<Asset> findByProducingJobIdAndProjectId(UUID producingJobId, UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO assets (
                id, filename, size, content_type, storage_url, upload_status, created_at,
                project_id, producing_job_id, parent_asset_id, external_subject_id, external_reference
            ) VALUES (
                :id, :filename, :size, :contentType, :storageUrl, :uploadStatus, :createdAt,
                :projectId, :producingJobId, :parentAssetId, :externalSubjectId, :externalReference
            )
            ON CONFLICT (producing_job_id) DO UPDATE SET
                filename = EXCLUDED.filename,
                size = EXCLUDED.size,
                content_type = EXCLUDED.content_type,
                storage_url = EXCLUDED.storage_url,
                upload_status = EXCLUDED.upload_status,
                parent_asset_id = EXCLUDED.parent_asset_id,
                external_subject_id = EXCLUDED.external_subject_id,
                external_reference = EXCLUDED.external_reference
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
                             @Param("parentAssetId") UUID parentAssetId,
                             @Param("externalSubjectId") String externalSubjectId,
                             @Param("externalReference") String externalReference);
}

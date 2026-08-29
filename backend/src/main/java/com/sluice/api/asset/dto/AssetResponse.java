package com.sluice.api.asset.dto;

import com.sluice.api.asset.domain.Asset;
import java.time.Instant;
import java.util.UUID;

public class AssetResponse {
    private UUID id;
    private String filename;
    private long size;
    private String contentType;
    private String uploadStatus;
    private Instant createdAt;
    private UUID parentAssetId;
    private UUID producingJobId;
    private String externalSubjectId;
    private String externalReference;

    public AssetResponse(UUID id, String filename, long size, String contentType, String uploadStatus,
                         Instant createdAt, UUID parentAssetId, UUID producingJobId) {
        this(id, filename, size, contentType, uploadStatus, createdAt, parentAssetId, producingJobId, null, null);
    }

    public AssetResponse(UUID id, String filename, long size, String contentType, String uploadStatus,
                         Instant createdAt, UUID parentAssetId, UUID producingJobId,
                         String externalSubjectId, String externalReference) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.uploadStatus = uploadStatus;
        this.createdAt = createdAt;
        this.parentAssetId = parentAssetId;
        this.producingJobId = producingJobId;
        this.externalSubjectId = externalSubjectId;
        this.externalReference = externalReference;
    }

    /**
     * Maps durable asset facts to the browser/API contract without exposing the private blob location.
     * Downloads continue to be resolved server-side through the short-lived download endpoint.
     */
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.getId(), asset.getFilename(), asset.getSize(), asset.getContentType(),
                asset.getUploadStatus().name(), asset.getCreatedAt(),
                asset.getParentAssetId(), asset.getProducingJobId(),
                asset.getExternalSubjectId(), asset.getExternalReference());
    }

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public String getContentType() { return contentType; }
    public String getUploadStatus() { return uploadStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getParentAssetId() { return parentAssetId; }
    public UUID getProducingJobId() { return producingJobId; }

    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Caller-supplied opaque subject correlation ID. Never an authorization claim.",
            example = "user_123", nullable = true)
    public String getExternalSubjectId() { return externalSubjectId; }

    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Caller-supplied opaque media or grouping reference. Never an authorization claim.",
            example = "avatar_2026_08", nullable = true)
    public String getExternalReference() { return externalReference; }
}

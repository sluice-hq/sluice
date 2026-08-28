package com.sluice.api.asset.dto;

import java.time.Instant;
import java.util.UUID;

public class UploadAssetResponse {
    private UUID assetId;
    private String filename;
    private long size;
    private String contentType;
    private Instant assetCreatedAt;
    
    private UUID jobId;
    private String jobStatus;
    private Instant jobCreatedAt;

    public UploadAssetResponse() {
    }

    public UploadAssetResponse(UUID assetId, String filename, long size, String contentType, Instant assetCreatedAt, UUID jobId, String jobStatus, Instant jobCreatedAt) {
        this.assetId = assetId;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.assetCreatedAt = assetCreatedAt;
        this.jobId = jobId;
        this.jobStatus = jobStatus;
        this.jobCreatedAt = jobCreatedAt;
    }

    /**
     * Source-compatible overload for older callers. The private storage URL is
     * intentionally ignored and is never serialized into the public response.
     */
    @Deprecated
    public UploadAssetResponse(UUID assetId, String filename, long size, String contentType, String ignoredStorageUrl,
                               Instant assetCreatedAt, UUID jobId, String jobStatus, Instant jobCreatedAt) {
        this(assetId, filename, size, contentType, assetCreatedAt, jobId, jobStatus, jobCreatedAt);
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getAssetCreatedAt() {
        return assetCreatedAt;
    }

    public void setAssetCreatedAt(Instant assetCreatedAt) {
        this.assetCreatedAt = assetCreatedAt;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(String jobStatus) {
        this.jobStatus = jobStatus;
    }

    public Instant getJobCreatedAt() {
        return jobCreatedAt;
    }

    public void setJobCreatedAt(Instant jobCreatedAt) {
        this.jobCreatedAt = jobCreatedAt;
    }
}

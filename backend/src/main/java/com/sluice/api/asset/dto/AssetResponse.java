package com.sluice.api.asset.dto;

import java.time.Instant;
import java.util.UUID;

public class AssetResponse {
    private UUID id;
    private String filename;
    private long size;
    private String contentType;
    private String storageUrl;
    private String uploadStatus;
    private Instant createdAt;

    public AssetResponse(UUID id, String filename, long size, String contentType, String storageUrl, String uploadStatus, Instant createdAt) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.storageUrl = storageUrl;
        this.uploadStatus = uploadStatus;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public String getContentType() { return contentType; }
    public String getStorageUrl() { return storageUrl; }
    public String getUploadStatus() { return uploadStatus; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.sluice.api.asset.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {
    @Id
    private UUID id;
    private String filename;
    private long size;
    private String contentType;
    private String storageUrl;
    @Enumerated(EnumType.STRING)
    private UploadStatus uploadStatus;
    private Instant createdAt;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "parent_asset_id")
    private Asset parentAsset;

    public enum UploadStatus {
        PENDING,
        COMPLETED
    }

    public Asset() {}

    public Asset(UUID id, String filename, long size, String contentType, String storageUrl, UploadStatus uploadStatus, Instant createdAt) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.storageUrl = storageUrl;
        this.uploadStatus = uploadStatus;
        this.createdAt = createdAt;
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }

    public UploadStatus getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(UploadStatus uploadStatus) { this.uploadStatus = uploadStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Asset getParentAsset() { return parentAsset; }
    public void setParentAsset(Asset parentAsset) { this.parentAsset = parentAsset; }
}

package com.sluice.api.asset.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private Instant createdAt;

    public Asset() {}

    public Asset(UUID id, String filename, long size, String contentType, String storageUrl, Instant createdAt) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.storageUrl = storageUrl;
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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

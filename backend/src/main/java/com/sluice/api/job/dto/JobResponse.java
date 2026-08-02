package com.sluice.api.job.dto;

import java.time.Instant;
import java.util.UUID;

public class JobResponse {
    private UUID id;
    private UUID assetId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public JobResponse() {
    }

    public JobResponse(UUID id, UUID assetId, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.assetId = assetId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

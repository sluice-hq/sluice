package com.sluice.api.messaging.dto;

import java.util.UUID;

public class JobMessage {
    private UUID jobId;
    private UUID assetId;

    public JobMessage() {
    }

    public JobMessage(UUID jobId, UUID assetId) {
        this.jobId = jobId;
        this.assetId = assetId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }
}

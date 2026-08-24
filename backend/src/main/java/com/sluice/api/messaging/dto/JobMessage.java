package com.sluice.api.messaging.dto;

import java.util.UUID;

public class JobMessage {
    private UUID jobId;
    private UUID assetId;
    private String requestId;

    public JobMessage() {
    }

    public JobMessage(UUID jobId, UUID assetId) {
        this.jobId = jobId;
        this.assetId = assetId;
    }

    public JobMessage(UUID jobId, UUID assetId, String requestId) {
        this(jobId, assetId);
        this.requestId = requestId;
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

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}

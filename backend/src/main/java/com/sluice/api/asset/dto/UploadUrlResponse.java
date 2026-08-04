package com.sluice.api.asset.dto;

import java.util.UUID;

public class UploadUrlResponse {
    private UUID assetId;
    private String uploadUrl;
    private String blobName;

    public UploadUrlResponse() {}

    public UploadUrlResponse(UUID assetId, String uploadUrl, String blobName) {
        this.assetId = assetId;
        this.uploadUrl = uploadUrl;
        this.blobName = blobName;
    }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }

    public String getBlobName() { return blobName; }
    public void setBlobName(String blobName) { this.blobName = blobName; }
}

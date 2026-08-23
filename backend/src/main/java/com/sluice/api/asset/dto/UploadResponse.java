package com.sluice.api.asset.dto;

import com.sluice.api.asset.domain.Asset;

import java.time.Instant;
import java.util.UUID;

public record UploadResponse(UUID assetId, String filename, long size, String contentType,
                             String uploadStatus, Instant createdAt) {
    public static UploadResponse from(Asset asset) {
        return new UploadResponse(asset.getId(), asset.getFilename(), asset.getSize(), asset.getContentType(),
                asset.getUploadStatus().name(), asset.getCreatedAt());
    }
}

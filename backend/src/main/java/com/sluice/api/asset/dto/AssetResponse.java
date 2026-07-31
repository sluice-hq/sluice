package com.sluice.api.asset.dto;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String filename,
        long size,
        String contentType,
        String storageUrl,
        Instant createdAt
) {
}

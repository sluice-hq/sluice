package com.sluice.api.asset.controller;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.service.AssetService;
import com.sluice.api.asset.service.UploadService;
import com.sluice.api.asset.dto.UploadUrlRequest;
import com.sluice.api.asset.dto.UploadUrlResponse;
import com.sluice.api.auth.domain.ProjectContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UploadControllerTest {
    @Test
    void uploadCreationForwardsTheRequiredIdempotencyKey() {
        ProjectContext context = new ProjectContext(UUID.randomUUID(), null, true);
        AssetService assets = mock(AssetService.class);
        UploadService uploads = mock(UploadService.class);
        UUID assetId = UUID.randomUUID();
        when(uploads.create("input.png", "image/png", 12, "user_123", "avatar_1", "request-1", context))
                .thenReturn(new UploadUrlResponse(assetId, "upload-url?sig=value", assetId + ".png"));

        var response = new UploadController(assets, uploads)
                .create(new UploadUrlRequest("input.png", "image/png", 12, "user_123", "avatar_1"),
                        "request-1", context);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(assetId, response.getBody().getAssetId());
        verify(uploads).create("input.png", "image/png", 12,
                "user_123", "avatar_1", "request-1", context);
    }

    @Test
    void repeatedCompletionWithTheSameKeyReturnsTheOriginalAsset() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.png", 12, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        AssetService assets = mock(AssetService.class);
        UploadService uploads = mock(UploadService.class);
        when(uploads.complete(assetId, "upload-1", context)).thenReturn(asset);

        var response = new UploadController(assets, uploads)
                .complete(assetId, "upload-1", context);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(assetId, response.getBody().assetId());
        verify(uploads).complete(assetId, "upload-1", context);
    }
}

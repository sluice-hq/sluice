package com.sluice.api.asset.controller;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.service.AssetService;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AssetControllerTest {
    @Test
    void rejectsDownloadForPendingAsset() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset pending = new Asset(assetId, "input.png", 12, "image/png", "blob-url",
                Asset.UploadStatus.PENDING, Instant.now(), projectId);
        AssetService assets = mock(AssetService.class);
        StorageService storage = mock(StorageService.class);
        when(assets.getAsset(assetId, context)).thenReturn(Optional.of(pending));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new AssetController(assets, storage).getDownloadUrl(assetId, context));

        assertEquals("Asset is not ready for download", error.getMessage());
        verify(storage, never()).generateDownloadUrl(anyString());
    }

    @Test
    void generatesDownloadForCompletedAsset() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset completed = new Asset(assetId, "input.png", 12, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        AssetService assets = mock(AssetService.class);
        StorageService storage = mock(StorageService.class);
        when(assets.getAsset(assetId, context)).thenReturn(Optional.of(completed));
        when(storage.generateDownloadUrl("blob-url")).thenReturn("signed-url");

        var response = new AssetController(assets, storage).getDownloadUrl(assetId, context);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("signed-url", response.getBody().downloadUrl());
    }
}

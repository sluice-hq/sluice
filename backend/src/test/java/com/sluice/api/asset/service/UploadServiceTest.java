package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UploadServiceTest {
    @Test
    void repeatedUploadRequestReturnsTheExistingAssetWithAFreshScopedUrl() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset pending = asset(assetId, Asset.UploadStatus.PENDING, projectId);
        AssetService assets = mock(AssetService.class);
        AssetRepository repository = mock(AssetRepository.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(eq(projectId), eq(IdempotencyService.UPLOAD_CREATE), eq("request-1"), eq("hash"), any()))
                .thenReturn(new IdempotencyRecord(UUID.randomUUID(), projectId,
                        IdempotencyService.UPLOAD_CREATE, "request-1", "hash", assetId));
        when(repository.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(pending));
        when(assets.refreshUploadUrl(pending)).thenReturn(
                new com.sluice.api.asset.dto.UploadUrlResponse(assetId, "upload-url?sig=fresh", assetId + ".png"));

        var result = new UploadService(assets, repository, idempotency)
                .create("input.png", "image/png", 10, "request-1", context);

        assertEquals(assetId, result.getAssetId());
        verify(assets).refreshUploadUrl(pending);
        verify(assets, never()).requestUploadUrl(any(UUID.class), anyString(), anyString(), anyLong(), any(ProjectContext.class));
    }

    @Test
    void claimsAndCompletesTheFirstUploadRequestInOneFlow() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset pending = asset(assetId, Asset.UploadStatus.PENDING, projectId);
        Asset completed = asset(assetId, Asset.UploadStatus.COMPLETED, projectId);
        AssetService assets = mock(AssetService.class);
        AssetRepository repository = mock(AssetRepository.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(any(), any(), any(), any(), eq(assetId)))
                .thenReturn(new IdempotencyRecord(UUID.randomUUID(), projectId,
                        IdempotencyService.UPLOAD_COMPLETE, "key", "hash", assetId));
        when(repository.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(pending));
        when(assets.completeUpload(assetId, context)).thenReturn(completed);

        Asset result = new UploadService(assets, repository, idempotency).complete(assetId, "key", context);

        assertSame(completed, result);
        verify(assets).completeUpload(assetId, context);
    }

    @Test
    void completedReplayDoesNotVerifyStorageAgain() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset completed = asset(assetId, Asset.UploadStatus.COMPLETED, projectId);
        AssetService assets = mock(AssetService.class);
        AssetRepository repository = mock(AssetRepository.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(any(), any(), any(), any(), eq(assetId)))
                .thenReturn(new IdempotencyRecord(UUID.randomUUID(), projectId,
                        IdempotencyService.UPLOAD_COMPLETE, "key", "hash", assetId));
        when(repository.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(completed));

        assertSame(completed, new UploadService(assets, repository, idempotency)
                .complete(assetId, "key", context));
        verify(assets, never()).completeUpload(any(UUID.class), any(ProjectContext.class));
    }

    private Asset asset(UUID id, Asset.UploadStatus status, UUID projectId) {
        return new Asset(id, "input.png", 10, "image/png", "blob-url", status, Instant.now(), projectId);
    }
}

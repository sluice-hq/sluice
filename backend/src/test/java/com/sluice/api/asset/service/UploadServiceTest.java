package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
import com.sluice.api.idempotency.service.IdempotencyConflictException;
import com.sluice.api.idempotency.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UploadServiceTest {
    @Test
    void reusingAKeyWithDifferentExternalReferencesConflicts() {
        UUID projectId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        AssetService assets = mock(AssetService.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        IdempotencyRecordRepository records = mock(IdempotencyRecordRepository.class);
        java.util.concurrent.atomic.AtomicReference<IdempotencyRecord> stored =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(records.findByProjectIdAndOperationAndIdempotencyKey(
                projectId, IdempotencyService.UPLOAD_CREATE, "same-key"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(records.insertIfAbsent(any(UUID.class), eq(projectId), eq(IdempotencyService.UPLOAD_CREATE),
                eq("same-key"), anyString(), any(UUID.class))).thenAnswer(invocation -> {
                    stored.set(new IdempotencyRecord(invocation.getArgument(0), projectId,
                            IdempotencyService.UPLOAD_CREATE, "same-key", invocation.getArgument(4),
                            invocation.getArgument(5)));
                    return 1;
                });
        when(assets.requestUploadUrl(any(UUID.class), eq("input.png"), eq("image/png"), eq(10L),
                eq("user_123"), eq("avatar_1"), eq(context))).thenAnswer(invocation -> {
                    UUID assetId = invocation.getArgument(0);
                    return new com.sluice.api.asset.dto.UploadUrlResponse(
                            assetId, "upload-url?sig=value", assetId + ".png");
                });
        UploadService service = new UploadService(
                assets, assetRepository, new IdempotencyService(records));

        service.create("input.png", "image/png", 10,
                "user_123", "avatar_1", "same-key", context);

        assertThrows(IdempotencyConflictException.class, () -> service.create(
                "input.png", "image/png", 10,
                "user_123", "avatar_2", "same-key", context));
    }

    @Test
    void externalReferencesArePartOfTheIdempotencyFingerprint() {
        UUID projectId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        AssetService assets = mock(AssetService.class);
        AssetRepository repository = mock(AssetRepository.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.claim(eq(projectId), eq(IdempotencyService.UPLOAD_CREATE), anyString(),
                anyString(), any(UUID.class))).thenAnswer(invocation -> new IdempotencyRecord(
                        UUID.randomUUID(), projectId, IdempotencyService.UPLOAD_CREATE,
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));
        when(assets.requestUploadUrl(any(UUID.class), eq("input.png"), eq("image/png"), eq(10L),
                eq("user_123"), anyString(), eq(context))).thenAnswer(invocation -> {
                    UUID assetId = invocation.getArgument(0);
                    return new com.sluice.api.asset.dto.UploadUrlResponse(
                            assetId, "upload-url?sig=value", assetId + ".png");
                });
        UploadService service = new UploadService(assets, repository, idempotency);

        service.create("input.png", "image/png", 10, "user_123", "avatar_1", "key-1", context);
        service.create("input.png", "image/png", 10, "user_123", "avatar_2", "key-2", context);

        org.mockito.ArgumentCaptor<String> fingerprints = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(idempotency, times(2)).hash(fingerprints.capture());
        assertNotEquals(fingerprints.getAllValues().get(0), fingerprints.getAllValues().get(1));
    }

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

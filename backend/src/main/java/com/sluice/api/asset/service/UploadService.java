package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
import com.sluice.api.asset.dto.UploadUrlResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UploadService {
    private final AssetService assets;
    private final AssetRepository assetRepository;
    private final IdempotencyService idempotency;

    public UploadService(AssetService assets, AssetRepository assetRepository, IdempotencyService idempotency) {
        this.assets = assets;
        this.assetRepository = assetRepository;
        this.idempotency = idempotency;
    }

    @Transactional
    public UploadUrlResponse create(String filename, String contentType, long size, String key, ProjectContext context) {
        return create(filename, contentType, size, null, null, key, context);
    }

    @Transactional
    public UploadUrlResponse create(String filename, String contentType, long size,
                                    String externalSubjectId, String externalReference,
                                    String key, ProjectContext context) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        AssetReferencePolicy.validate(externalSubjectId, externalReference);
        String fingerprint = idempotency.hash(String.join("|", "upload-create", fingerprintPart(filename),
                fingerprintPart(contentType), Long.toString(size), fingerprintPart(externalSubjectId),
                fingerprintPart(externalReference)));
        UUID requestedAssetId = UUID.randomUUID();
        IdempotencyRecord claim = idempotency.claim(context.getProjectId(), IdempotencyService.UPLOAD_CREATE,
                key, fingerprint, requestedAssetId);
        if (claim != null && !claim.getResourceId().equals(requestedAssetId)) {
            Asset existing = assetRepository.findByIdAndProjectId(claim.getResourceId(), context.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent upload resource no longer exists"));
            return assets.refreshUploadUrl(existing);
        }
        return assets.requestUploadUrl(requestedAssetId, filename, contentType, size,
                externalSubjectId, externalReference, context);
    }

    @Transactional
    public Asset complete(UUID assetId, String key, ProjectContext context) {
        String fingerprint = idempotency.hash("upload-complete:" + assetId);
        IdempotencyRecord claim = idempotency.claim(context.getProjectId(), IdempotencyService.UPLOAD_COMPLETE,
                key, fingerprint, assetId);
        if (claim != null) {
            Asset asset = assetRepository.findByIdAndProjectId(claim.getResourceId(), context.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent upload resource no longer exists"));
            if (asset.getUploadStatus() == Asset.UploadStatus.COMPLETED) return asset;
        }
        return assets.completeUpload(assetId, context);
    }

    private String fingerprintPart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }
}

package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
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
}

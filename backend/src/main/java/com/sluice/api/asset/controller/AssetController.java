package com.sluice.api.asset.controller;

import com.sluice.api.asset.dto.UploadAssetResponse;
import com.sluice.api.asset.dto.DownloadUrlResponse;
import com.sluice.api.asset.dto.UploadUrlResponse;
import com.sluice.api.asset.service.AssetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.storage.StorageService;
import com.sluice.api.config.MediaSafetyPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final StorageService storageService;

    public AssetController(AssetService assetService, StorageService storageService) {
        this(assetService, storageService, new MediaSafetyPolicy(50 * 1024 * 1024, 255,
                "image/jpeg,image/png,image/gif,application/pdf,video/mp4"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AssetController(AssetService assetService, StorageService storageService, MediaSafetyPolicy safety) {
        this.assetService = assetService;
        this.storageService = storageService;
        this.safety = safety;
    }

    private final MediaSafetyPolicy safety;

    @GetMapping
    public ResponseEntity<Page<AssetResponse>> getAssets(
            @AuthenticationPrincipal ProjectContext context,
            Pageable pageable) {
        Page<AssetResponse> assets = assetService.getAssets(context, pageable).map(asset -> new AssetResponse(
                asset.getId(),
                asset.getFilename(),
                asset.getSize(),
                asset.getContentType(),
                asset.getStorageUrl(),
                asset.getUploadStatus().name(),
                asset.getCreatedAt()
        ));
        return ResponseEntity.ok(assets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAsset(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal ProjectContext context) {
        return assetService.getAsset(id, context)
                .map(asset -> new AssetResponse(
                        asset.getId(),
                        asset.getFilename(),
                        asset.getSize(),
                        asset.getContentType(),
                        asset.getStorageUrl(),
                        asset.getUploadStatus().name(),
                        asset.getCreatedAt()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * @deprecated Legacy multipart upload endpoint. Use the SAS upload workflow (/upload-url and /complete) instead.
     */
    @Deprecated
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UploadAssetResponse> uploadAsset(
            @RequestParam("file") MultipartFile file, 
            @RequestParam("pipelineId") java.util.UUID pipelineId,
            @AuthenticationPrincipal ProjectContext context) {
        safety.validate(file);

        UploadAssetResponse response = assetService.uploadAsset(file, pipelineId, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> requestUploadUrl(
            @RequestBody com.sluice.api.asset.dto.UploadUrlRequest request,
            @AuthenticationPrincipal ProjectContext context) {
        if (request == null) throw new IllegalArgumentException("Upload request is required");
        safety.validate(request.getFilename(), request.getContentType(), request.getSize());

        com.sluice.api.asset.dto.UploadUrlResponse response = assetService.requestUploadUrl(
                request.getFilename(), request.getContentType(), request.getSize(), context);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assetId}/complete")
    public ResponseEntity<UploadAssetResponse> completeUpload(
            @PathVariable java.util.UUID assetId, 
            @RequestParam("pipelineId") java.util.UUID pipelineId,
            @AuthenticationPrincipal ProjectContext context) {
        UploadAssetResponse response = assetService.completeUpload(assetId, pipelineId, context);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal ProjectContext context) {
        return assetService.getAsset(id, context)
                .map(asset -> {
                    String downloadUrl = storageService.generateDownloadUrl(asset.getStorageUrl());
                    return ResponseEntity.ok(new DownloadUrlResponse(downloadUrl));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

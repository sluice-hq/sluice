package com.sluice.api.asset.controller;

import com.sluice.api.asset.dto.UploadAssetResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final StorageService storageService;

    // Allowed content types for Milestone 1
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "application/pdf", "video/mp4");

    // Max size 50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    public AssetController(AssetService assetService, StorageService storageService) {
        this.assetService = assetService;
        this.storageService = storageService;
    }

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
    public ResponseEntity<?> uploadAsset(
            @RequestParam("file") MultipartFile file, 
            @RequestParam("pipelineId") java.util.UUID pipelineId,
            @AuthenticationPrincipal ProjectContext context) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File must not be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("File size exceeds the 50MB limit.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Unsupported content type. Allowed types: " + ALLOWED_CONTENT_TYPES);
        }

        UploadAssetResponse response = assetService.uploadAsset(file, pipelineId, context);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> requestUploadUrl(
            @RequestBody com.sluice.api.asset.dto.UploadUrlRequest request,
            @AuthenticationPrincipal ProjectContext context) {
        if (request.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("File size exceeds the 50MB limit.");
        }

        if (request.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Unsupported content type. Allowed types: " + ALLOWED_CONTENT_TYPES);
        }

        com.sluice.api.asset.dto.UploadUrlResponse response = assetService.requestUploadUrl(
                request.getFilename(), request.getContentType(), request.getSize(), context);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assetId}/complete")
    public ResponseEntity<?> completeUpload(
            @PathVariable java.util.UUID assetId, 
            @RequestParam("pipelineId") java.util.UUID pipelineId,
            @AuthenticationPrincipal ProjectContext context) {
        UploadAssetResponse response = assetService.completeUpload(assetId, pipelineId, context);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable java.util.UUID id,
            @AuthenticationPrincipal ProjectContext context) {
        return assetService.getAsset(id, context)
                .map(asset -> {
                    String downloadUrl = storageService.generateDownloadUrl(asset.getStorageUrl());
                    return ResponseEntity.ok(java.util.Map.of("downloadUrl", downloadUrl));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

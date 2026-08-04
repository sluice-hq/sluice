package com.sluice.api.asset.controller;

import com.sluice.api.asset.dto.UploadAssetResponse;
import com.sluice.api.asset.service.AssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    // Allowed content types for Milestone 1
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "application/pdf", "video/mp4");

    // Max size 50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * @deprecated Legacy multipart upload endpoint. Use the SAS upload workflow (/upload-url and /complete) instead.
     */
    @Deprecated
    @PostMapping
    public ResponseEntity<?> uploadAsset(@RequestParam("file") MultipartFile file) {
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

        UploadAssetResponse response = assetService.uploadAsset(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> requestUploadUrl(@RequestBody com.sluice.api.asset.dto.UploadUrlRequest request) {
        if (request.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("File size exceeds the 50MB limit.");
        }

        if (request.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Unsupported content type. Allowed types: " + ALLOWED_CONTENT_TYPES);
        }

        com.sluice.api.asset.dto.UploadUrlResponse response = assetService.requestUploadUrl(
                request.getFilename(), request.getContentType(), request.getSize());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assetId}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable java.util.UUID assetId) {
        try {
            UploadAssetResponse response = assetService.completeUpload(assetId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

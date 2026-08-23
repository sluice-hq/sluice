package com.sluice.api.asset.controller;

import com.sluice.api.asset.dto.UploadResponse;
import com.sluice.api.asset.dto.UploadUrlRequest;
import com.sluice.api.asset.dto.UploadUrlResponse;
import com.sluice.api.asset.service.AssetService;
import com.sluice.api.asset.service.UploadService;
import com.sluice.api.auth.domain.ProjectContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "application/pdf", "video/mp4");

    private final AssetService assets;
    private final UploadService uploads;

    public UploadController(AssetService assets, UploadService uploads) {
        this.assets = assets;
        this.uploads = uploads;
    }

    @PostMapping
    public ResponseEntity<UploadUrlResponse> create(@RequestBody UploadUrlRequest request,
                                                    @AuthenticationPrincipal ProjectContext context) {
        validate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assets.requestUploadUrl(request.getFilename(), request.getContentType(), request.getSize(), context));
    }

    @PostMapping("/{assetId}/complete")
    public ResponseEntity<UploadResponse> complete(@PathVariable UUID assetId,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                   @AuthenticationPrincipal ProjectContext context) {
        var asset = uploads.complete(assetId, key, context);
        return ResponseEntity.ok(UploadResponse.from(asset));
    }

    private void validate(UploadUrlRequest request) {
        if (request == null || request.getFilename() == null || request.getFilename().isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        if (request.getSize() <= 0 || request.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must be between 1 byte and 50MB");
        }
        if (request.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
            throw new IllegalArgumentException("Unsupported content type");
        }
    }
}

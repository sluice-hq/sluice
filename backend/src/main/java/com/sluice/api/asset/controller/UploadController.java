package com.sluice.api.asset.controller;

import com.sluice.api.asset.dto.UploadResponse;
import com.sluice.api.asset.dto.UploadUrlRequest;
import com.sluice.api.asset.dto.UploadUrlResponse;
import com.sluice.api.asset.service.AssetService;
import com.sluice.api.asset.service.UploadService;
import com.sluice.api.asset.service.AssetReferencePolicy;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.config.MediaSafetyPolicy;
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
    private final AssetService assets;
    private final UploadService uploads;
    private final MediaSafetyPolicy safety;

    public UploadController(AssetService assets, UploadService uploads) {
        this(assets, uploads, new MediaSafetyPolicy(50 * 1024 * 1024, 255,
                "image/jpeg,image/png,image/gif,application/pdf,video/mp4"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UploadController(AssetService assets, UploadService uploads, MediaSafetyPolicy safety) {
        this.assets = assets;
        this.uploads = uploads;
        this.safety = safety;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UploadUrlResponse> create(@RequestBody UploadUrlRequest request,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @AuthenticationPrincipal ProjectContext context) {
        validate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uploads.create(request.getFilename(), request.getContentType(), request.getSize(),
                        request.getExternalSubjectId(), request.getExternalReference(), key, context));
    }

    @PostMapping("/{assetId}/complete")
    public ResponseEntity<UploadResponse> complete(@PathVariable UUID assetId,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                   @AuthenticationPrincipal ProjectContext context) {
        var asset = uploads.complete(assetId, key, context);
        return ResponseEntity.ok(UploadResponse.from(asset));
    }

    private void validate(UploadUrlRequest request) {
        if (request == null) throw new IllegalArgumentException("Upload request is required");
        safety.validate(request.getFilename(), request.getContentType(), request.getSize());
        AssetReferencePolicy.validate(request.getExternalSubjectId(), request.getExternalReference());
    }
}

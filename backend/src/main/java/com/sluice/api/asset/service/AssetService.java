package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.dto.UploadAssetResponse;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.run.service.RunService;
import com.sluice.api.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.sluice.api.auth.domain.ProjectContext;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AssetService {
    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final int MAX_FILENAME_FILTER_LENGTH = 255;
    private static final int MAX_MEDIA_TYPE_LENGTH = 64;
    private static final java.util.regex.Pattern MEDIA_TYPE_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]*");

    public record AssetFilters(String filename, Asset.UploadStatus status, String mediaType,
                               Instant createdFrom, Instant createdBefore,
                               String externalSubjectId, String externalReference) {
        public static AssetFilters empty() {
            return new AssetFilters(null, null, null, null, null, null, null);
        }
    }

    private final StorageService storageService;
    private final AssetRepository assetRepository;
    private final MediaContentVerifier mediaContentVerifier;
    private final RunService runService;

    public AssetService(StorageService storageService, AssetRepository assetRepository,
                        MediaContentVerifier mediaContentVerifier, RunService runService) {
        this.storageService = storageService;
        this.assetRepository = assetRepository;
        this.mediaContentVerifier = mediaContentVerifier;
        this.runService = runService;
    }

    public Page<Asset> getAssets(ProjectContext context, Pageable pageable) {
        return getAssets(context, AssetFilters.empty(), pageable);
    }

    public Page<Asset> getAssets(ProjectContext context, String externalSubjectId,
                                 String externalReference, Pageable pageable) {
        return getAssets(context,
                new AssetFilters(null, null, null, null, null, externalSubjectId, externalReference), pageable);
    }

    public Page<Asset> getAssets(ProjectContext context, AssetFilters filters, Pageable pageable) {
        AssetFilters normalized = normalize(filters == null ? AssetFilters.empty() : filters);
        if (normalized.equals(AssetFilters.empty())) {
            return assetRepository.findAllByProjectId(context.getProjectId(), pageable);
        }
        return assetRepository.searchAssets(
                context.getProjectId(),
                normalized.filename() != null, normalized.filename(),
                normalized.status() != null, normalized.status(),
                normalized.mediaType() != null, normalized.mediaType(),
                normalized.createdFrom() != null, normalized.createdFrom(),
                normalized.createdBefore() != null, normalized.createdBefore(),
                normalized.externalSubjectId() != null, normalized.externalSubjectId(),
                normalized.externalReference() != null, normalized.externalReference(),
                pageable);
    }

    private AssetFilters normalize(AssetFilters filters) {
        String filename = normalizeOptional(filters.filename());
        if (filename != null && filename.length() > MAX_FILENAME_FILTER_LENGTH) {
            throw new IllegalArgumentException("filename filter must not exceed " + MAX_FILENAME_FILTER_LENGTH + " characters");
        }
        String mediaType = normalizeOptional(filters.mediaType());
        if (mediaType != null && (mediaType.length() > MAX_MEDIA_TYPE_LENGTH
                || !MEDIA_TYPE_PATTERN.matcher(mediaType).matches())) {
            throw new IllegalArgumentException("mediaType filter is invalid");
        }
        if (filters.createdFrom() != null && filters.createdBefore() != null
                && !filters.createdFrom().isBefore(filters.createdBefore())) {
            throw new IllegalArgumentException("createdFrom must be before createdBefore");
        }
        AssetReferencePolicy.validate(filters.externalSubjectId(), filters.externalReference());
        return new AssetFilters(filename == null ? null : filename.toLowerCase(Locale.ROOT), filters.status(),
                mediaType == null ? null : mediaType.toLowerCase(Locale.ROOT), filters.createdFrom(),
                filters.createdBefore(), filters.externalSubjectId(), filters.externalReference());
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public Optional<Asset> getAsset(UUID assetId, ProjectContext context) {
        return assetRepository.findByIdAndProjectId(assetId, context.getProjectId());
    }

    @Transactional
    public UploadAssetResponse uploadAsset(MultipartFile file, java.util.UUID pipelineId, ProjectContext context) {
        String fileUrl;
        try {
            fileUrl = storageService.uploadFile(file);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload file to storage", e);
        }

        try {
            // Create asset record
            Asset asset = new Asset(
                    UUID.randomUUID(),
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType(),
                    fileUrl,
                    Asset.UploadStatus.COMPLETED,
                    Instant.now(),
                    context.getProjectId()
            );

            // The save operation is transactional by default in Spring Data JPA
            Asset savedAsset = assetRepository.save(asset);
            
            Job job = runService.createLegacy(savedAsset.getId(), pipelineId, context);
            
            return new UploadAssetResponse(
                    savedAsset.getId(),
                    savedAsset.getFilename(),
                    savedAsset.getSize(),
                    savedAsset.getContentType(),
                    savedAsset.getCreatedAt(),
                    job.getId(),
                    job.getStatus().name(),
                    job.getCreatedAt()
            );
        } catch (Exception e) {
            // Compensating action: delete the orphaned blob
            try {
                storageService.deleteFile(fileUrl);
            } catch (Exception cleanupException) {
                // In a production app, we would log this failure to a DLQ or alerting system
                log.error("asset_compensation_failed storageUrl={}", fileUrl, cleanupException);
            }
            throw new RuntimeException("Failed to persist asset metadata or job. Initiated blob cleanup.", e);
        }
    }

    public com.sluice.api.asset.dto.UploadUrlResponse requestUploadUrl(String filename, String contentType, long size, ProjectContext context) {
        return requestUploadUrl(UUID.randomUUID(), filename, contentType, size, null, null, context);
    }

    public com.sluice.api.asset.dto.UploadUrlResponse requestUploadUrl(
            String filename, String contentType, long size, String externalSubjectId,
            String externalReference, ProjectContext context) {
        return requestUploadUrl(UUID.randomUUID(), filename, contentType, size,
                externalSubjectId, externalReference, context);
    }

    @Transactional
    public com.sluice.api.asset.dto.UploadUrlResponse requestUploadUrl(UUID assetId, String filename,
                                                                       String contentType, long size,
                                                                       ProjectContext context) {
        return requestUploadUrl(assetId, filename, contentType, size, null, null, context);
    }

    @Transactional
    public com.sluice.api.asset.dto.UploadUrlResponse requestUploadUrl(UUID assetId, String filename,
                                                                       String contentType, long size,
                                                                       String externalSubjectId,
                                                                       String externalReference,
                                                                       ProjectContext context) {
        AssetReferencePolicy.validate(externalSubjectId, externalReference);
        String blobName = uploadBlobName(assetId, filename);
        String uploadUrl = storageService.generateUploadUrl(blobName, contentType);
        int queryStart = uploadUrl.indexOf("?");
        if (queryStart < 1) throw new IllegalStateException("Storage did not return a scoped upload URL");

        Asset asset = new Asset(assetId, filename, size, contentType, uploadUrl.substring(0, queryStart),
                Asset.UploadStatus.PENDING, Instant.now(), context.getProjectId(),
                externalSubjectId, externalReference);
        assetRepository.save(asset);
        return new com.sluice.api.asset.dto.UploadUrlResponse(asset.getId(), uploadUrl, blobName);
    }

    public com.sluice.api.asset.dto.UploadUrlResponse refreshUploadUrl(Asset asset) {
        if (asset.getUploadStatus() != Asset.UploadStatus.PENDING) {
            throw new IllegalStateException("Idempotent upload request already completed");
        }
        String blobName = uploadBlobName(asset.getId(), asset.getFilename());
        String uploadUrl = storageService.generateUploadUrl(blobName, asset.getContentType());
        return new com.sluice.api.asset.dto.UploadUrlResponse(asset.getId(), uploadUrl, blobName);
    }

    private String uploadBlobName(UUID assetId, String filename) {
        String extension = "";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf("."));
        }
        return assetId + extension;
    }

    @Transactional
    public UploadAssetResponse completeUpload(UUID assetId, UUID pipelineId, ProjectContext context) {
        Asset asset = assetRepository.findByIdAndProjectId(assetId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getUploadStatus() != Asset.UploadStatus.PENDING) {
            throw new IllegalStateException("Asset is not in PENDING state");
        }

        if (!storageService.fileExists(asset.getStorageUrl())) {
            throw new RuntimeException("File does not exist in storage");
        }
        
        long actualSize = storageService.getFileSize(asset.getStorageUrl());
        if (actualSize != asset.getSize()) {
            throw new RuntimeException("Uploaded file size (" + actualSize + " bytes) does not match expected size (" + asset.getSize() + " bytes)");
        }
        if (mediaContentVerifier != null) mediaContentVerifier.verify(asset.getStorageUrl(), asset.getContentType());
        
        asset.setUploadStatus(Asset.UploadStatus.COMPLETED);
        Asset savedAsset = assetRepository.save(asset);
        
        Job job = runService.createLegacy(savedAsset.getId(), pipelineId, context);
        
        return new UploadAssetResponse(
                asset.getId(),
                asset.getFilename(),
                asset.getSize(),
                asset.getContentType(),
                asset.getCreatedAt(),
                job.getId(),
                job.getStatus().name(),
                job.getCreatedAt()
        );
    }

    /**
     * Finalizes a direct upload without starting processing. The public /uploads API
     * uses this method so the same completed asset can be run through multiple pipelines.
     */
    @Transactional
    public Asset completeUpload(UUID assetId, ProjectContext context) {
        Asset asset = assetRepository.findByIdAndProjectId(assetId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getUploadStatus() == Asset.UploadStatus.COMPLETED) {
            return asset;
        }
        if (!storageService.fileExists(asset.getStorageUrl())) {
            throw new IllegalStateException("Uploaded file does not exist in storage");
        }

        long actualSize = storageService.getFileSize(asset.getStorageUrl());
        if (actualSize != asset.getSize()) {
            throw new IllegalStateException("Uploaded file size does not match the declared size");
        }
        if (mediaContentVerifier != null) mediaContentVerifier.verify(asset.getStorageUrl(), asset.getContentType());

        asset.setUploadStatus(Asset.UploadStatus.COMPLETED);
        return assetRepository.save(asset);
    }

}

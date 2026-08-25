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
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AssetService {
    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

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
        return assetRepository.findAllByProjectId(context.getProjectId(), pageable);
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
                    savedAsset.getStorageUrl(),
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
        String extension = "";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf("."));
        }
        String blobName = UUID.randomUUID().toString() + extension;
        
        String uploadUrl = storageService.generateUploadUrl(blobName, contentType);
        
        // Extract the permanent storage URL by stripping the SAS token (everything after the '?')
        String storageUrl = uploadUrl.substring(0, uploadUrl.indexOf("?"));
        
        Asset asset = new Asset(
                UUID.randomUUID(),
                filename,
                size,
                contentType,
                storageUrl,
                Asset.UploadStatus.PENDING,
                Instant.now(),
                context.getProjectId()
        );
        
        assetRepository.save(asset);
        
        return new com.sluice.api.asset.dto.UploadUrlResponse(asset.getId(), uploadUrl, blobName);
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
                asset.getStorageUrl(),
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

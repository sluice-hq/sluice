package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.dto.UploadAssetResponse;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.JobPublisher;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class AssetService {

    private final StorageService storageService;
    private final AssetRepository assetRepository;
    private final JobService jobService;
    private final JobPublisher jobPublisher;

    public AssetService(StorageService storageService, AssetRepository assetRepository, JobService jobService, JobPublisher jobPublisher) {
        this.storageService = storageService;
        this.assetRepository = assetRepository;
        this.jobService = jobService;
        this.jobPublisher = jobPublisher;
    }

    public Page<Asset> getAssets(Pageable pageable) {
        return assetRepository.findAll(pageable);
    }

    public Optional<Asset> getAsset(UUID assetId) {
        return assetRepository.findById(assetId);
    }

    public UploadAssetResponse uploadAsset(MultipartFile file) {
        String fileUrl;
        try {
            fileUrl = storageService.uploadFile(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to storage", e);
        }

        try {
            Asset asset = new Asset(
                    UUID.randomUUID(),
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType(),
                    fileUrl,
                    Asset.UploadStatus.COMPLETED,
                    Instant.now()
            );

            // The save operation is transactional by default in Spring Data JPA
            Asset savedAsset = assetRepository.save(asset);
            
            Job job = jobService.createJob(savedAsset.getId());
            jobPublisher.publishJob(new JobMessage(job.getId(), savedAsset.getId()));
            
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
                System.err.println("Failed to delete orphaned blob during compensation: " + fileUrl);
            }
            throw new RuntimeException("Failed to persist asset metadata or job. Initiated blob cleanup.", e);
        }
    }

    public com.sluice.api.asset.dto.UploadUrlResponse requestUploadUrl(String filename, String contentType, long size) {
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
                Instant.now()
        );
        
        assetRepository.save(asset);
        
        return new com.sluice.api.asset.dto.UploadUrlResponse(asset.getId(), uploadUrl, blobName);
    }

    public UploadAssetResponse completeUpload(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found"));
                
        if (asset.getUploadStatus() == Asset.UploadStatus.COMPLETED) {
            throw new RuntimeException("Upload already completed");
        }
        
        if (!storageService.fileExists(asset.getStorageUrl())) {
            throw new RuntimeException("File does not exist in storage");
        }
        
        long actualSize = storageService.getFileSize(asset.getStorageUrl());
        if (actualSize != asset.getSize()) {
            throw new RuntimeException("Uploaded file size (" + actualSize + " bytes) does not match expected size (" + asset.getSize() + " bytes)");
        }
        
        asset.setUploadStatus(Asset.UploadStatus.COMPLETED);
        assetRepository.save(asset);
        
        Job job = jobService.createJob(asset.getId());
        jobPublisher.publishJob(new JobMessage(job.getId(), asset.getId()));
        
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
}

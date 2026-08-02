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
import java.util.UUID;

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
}

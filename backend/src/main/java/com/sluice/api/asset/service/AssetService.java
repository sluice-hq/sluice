package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.asset.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class AssetService {

    private final StorageService storageService;
    private final AssetRepository assetRepository;

    public AssetService(StorageService storageService, AssetRepository assetRepository) {
        this.storageService = storageService;
        this.assetRepository = assetRepository;
    }

    public AssetResponse uploadAsset(MultipartFile file) {
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
            
            return new AssetResponse(
                    savedAsset.getId(),
                    savedAsset.getFilename(),
                    savedAsset.getSize(),
                    savedAsset.getContentType(),
                    savedAsset.getStorageUrl(),
                    savedAsset.getCreatedAt()
            );
        } catch (Exception e) {
            // Compensating action: delete the orphaned blob
            try {
                storageService.deleteFile(fileUrl);
            } catch (Exception cleanupException) {
                // In a production app, we would log this failure to a DLQ or alerting system
                System.err.println("Failed to delete orphaned blob during compensation: " + fileUrl);
            }
            throw new RuntimeException("Failed to persist asset metadata. Initiated blob cleanup.", e);
        }
    }
}

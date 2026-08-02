package com.sluice.api.worker;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.RabbitMqConfig;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.storage.StorageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;

@Service
public class JobWorker {

    private final JobService jobService;
    private final AssetRepository assetRepository;
    private final StorageService storageService;

    public JobWorker(JobService jobService, AssetRepository assetRepository, StorageService storageService) {
        this.jobService = jobService;
        this.assetRepository = assetRepository;
        this.storageService = storageService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void processJob(JobMessage message) {
        try {
            // Update status to RUNNING
            Job job = jobService.updateJobStatus(message.getJobId(), JobStatus.RUNNING);

            // Fetch the asset
            Asset asset = assetRepository.findById(message.getAssetId())
                    .orElseThrow(() -> new RuntimeException("Asset not found"));

            // Perform a genuine processing task: compute SHA-256 checksum
            byte[] fileBytes = storageService.downloadFile(asset.getStorageUrl());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String checksum = hexString.toString();
            
            System.out.println("Computed SHA-256 Checksum for Job " + job.getId() + ": " + checksum);

            // Update status to COMPLETED
            jobService.updateJobStatus(job.getId(), JobStatus.COMPLETED);
        } catch (Exception e) {
            System.err.println("Job processing failed: " + e.getMessage());
            jobService.updateJobStatus(message.getJobId(), JobStatus.FAILED);
        }
    }
}

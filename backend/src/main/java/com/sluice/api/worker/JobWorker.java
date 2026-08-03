package com.sluice.api.worker;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.RabbitMqConfig;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.pipeline.Pipeline;
import com.sluice.api.pipeline.PipelineEngine;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.storage.StorageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class JobWorker {

    private final JobService jobService;
    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final PipelineEngine pipelineEngine;
    private final Pipeline pipeline;

    public JobWorker(JobService jobService, 
                     AssetRepository assetRepository, 
                     StorageService storageService,
                     PipelineEngine pipelineEngine,
                     @Qualifier("defaultImagePipeline") Pipeline pipeline) {
        this.jobService = jobService;
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.pipelineEngine = pipelineEngine;
        this.pipeline = pipeline;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void processJob(JobMessage message) {
        try {
            // Update status to RUNNING
            Job job = jobService.updateJobStatus(message.getJobId(), JobStatus.RUNNING);

            // Fetch the asset
            Asset asset = assetRepository.findById(message.getAssetId())
                    .orElseThrow(() -> new RuntimeException("Asset not found"));

            // Download file
            byte[] fileBytes = storageService.downloadFile(asset.getStorageUrl());
            
            // Create processing context and run pipeline
            ProcessingContext context = new ProcessingContext(job, asset, fileBytes);
            pipelineEngine.execute(pipeline, context);

            // Update status to COMPLETED
            jobService.updateJobStatus(job.getId(), JobStatus.COMPLETED);
        } catch (Exception e) {
            System.err.println("Job processing failed: " + e.getMessage());
            e.printStackTrace();
            jobService.updateJobStatus(message.getJobId(), JobStatus.FAILED);
        }
    }
}

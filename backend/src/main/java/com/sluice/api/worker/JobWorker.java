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
import com.sluice.api.pipeline.PipelineResolver;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import com.sluice.api.storage.StorageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class JobWorker {

    private final JobService jobService;
    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final PipelineEngine pipelineEngine;
    private final PipelineVersionRepository pipelineVersionRepository;
    private final PipelineResolver pipelineResolver;

    public JobWorker(JobService jobService, 
                     AssetRepository assetRepository, 
                     StorageService storageService,
                     PipelineEngine pipelineEngine,
                     PipelineVersionRepository pipelineVersionRepository,
                     PipelineResolver pipelineResolver) {
        this.jobService = jobService;
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.pipelineEngine = pipelineEngine;
        this.pipelineVersionRepository = pipelineVersionRepository;
        this.pipelineResolver = pipelineResolver;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void processJob(JobMessage message) throws Exception {
        try {
            // Fetch current job state
            Job job = jobService.getJob(message.getJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found: " + message.getJobId()));

            if (job.getStatus() != JobStatus.QUEUED) {
                System.out.println("Job " + job.getId() + " is already in state " + job.getStatus() + ". Skipping duplicate delivery.");
                return;
            }

            System.out.println("Processing Job: " + job.getId());

            // Update status to RUNNING (this is atomic via JPA @Version)
            job = jobService.updateJobStatus(message.getJobId(), JobStatus.RUNNING);

            // Fetch the asset
            Asset asset = assetRepository.findById(message.getAssetId())
                    .orElseThrow(() -> new RuntimeException("Asset not found"));

            // Download file as MediaResource
            com.sluice.api.pipeline.MediaResource currentResource = storageService.downloadFile(asset.getStorageUrl());
            
            // Resolve pipeline version
            java.util.UUID pipelineVersionId = job.getPipelineVersionId();
            PipelineVersion pipelineVersion = pipelineVersionRepository.findById(pipelineVersionId)
                    .orElseThrow(() -> new RuntimeException("PipelineVersion not found: " + pipelineVersionId));
            
            Pipeline pipeline = pipelineResolver.resolve(pipelineVersion.getDefinition());

            // Create processing context and run pipeline
            ProcessingContext context = new ProcessingContext(job, asset, currentResource);
            
            try {
                pipelineEngine.execute(pipeline, context);
            } finally {
                // PipelineEngine tracks and cleans resources in its finally block
            }

            // Save new derived assets if the pipeline created a new resource
            if (context.getCurrentResource() != currentResource && context.getCurrentResource() instanceof com.sluice.api.pipeline.FileMediaResource fmr) {
                // upload and create derived asset
                try (java.io.FileInputStream fis = new java.io.FileInputStream(fmr.getFile())) {
                    String newUrl = storageService.uploadFile(
                            fmr.getFile().getName(),
                            fmr.getContentType(),
                            fis,
                            fmr.getSize()
                    );
                    
                    Asset derived = new Asset(
                            java.util.UUID.randomUUID(),
                            fmr.getFile().getName(),
                            fmr.getSize(),
                            fmr.getContentType(),
                            newUrl,
                            Asset.UploadStatus.COMPLETED,
                            java.time.Instant.now()
                    );
                    derived.setParentAsset(asset);
                    assetRepository.save(derived);
                    System.out.println("Created derived asset " + derived.getId() + " from job " + job.getId());
                }
            }

            // Update status to COMPLETED
            jobService.updateJobStatus(job.getId(), JobStatus.COMPLETED);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            System.out.println("Job " + message.getJobId() + " overridden by another process (Optimistic Lock). Aborting.");
        } catch (Exception e) {
            System.err.println("Job " + message.getJobId() + " failed: " + e.getMessage());
            try {
                jobService.updateJobStatus(message.getJobId(), JobStatus.FAILED);
            } catch (Exception updateEx) {
                System.err.println("Could not update job to FAILED: " + updateEx.getMessage());
            }
        }
    }
}

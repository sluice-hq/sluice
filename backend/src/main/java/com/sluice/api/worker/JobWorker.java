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
import java.util.UUID;
import java.time.Instant;

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
        com.sluice.api.pipeline.MediaResource finalResource = null;
        ProcessingContext processingContext = null;
        try {
            // Fetch current job state
            Job job = jobService.getJobSystem(message.getJobId())
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + message.getJobId()));

            if (job.getStatus() != JobStatus.QUEUED) {
                System.out.println("Job " + job.getId() + " is already in state " + job.getStatus() + ". Skipping duplicate delivery.");
                return;
            }

            System.out.println("Processing Job: " + job.getId());

            // Atomically claim QUEUED -> RUNNING so duplicate deliveries cannot
            // execute the same job in parallel.
            job = jobService.claimQueuedJob(message.getJobId()).orElse(null);
            if (job == null) {
                System.out.println("Job " + message.getJobId() + " was claimed by another worker.");
                return;
            }

            // Fetch the asset
            Asset asset = assetRepository.findById(message.getAssetId())
                    .orElseThrow(() -> new RuntimeException("Asset not found"));

            // Download file as MediaResource
            com.sluice.api.pipeline.MediaResource currentResource = storageService.downloadFile(asset.getStorageUrl());
            finalResource = currentResource;
            
            // Resolve pipeline version
            java.util.UUID pipelineVersionId = job.getPipelineVersionId();
            PipelineVersion pipelineVersion = pipelineVersionRepository.findById(pipelineVersionId)
                    .orElseThrow(() -> new RuntimeException("PipelineVersion not found: " + pipelineVersionId));
            
            Pipeline pipeline = pipelineResolver.resolve(pipelineVersion.getDefinition());

            // Create processing context and run pipeline
            ProcessingContext context = new ProcessingContext(job, asset, currentResource);
            processingContext = context;
            
            pipelineEngine.execute(pipeline, context);
            finalResource = context.getCurrentResource();

            // Save new derived assets if the pipeline created a new resource
            if (context.getCurrentResource() != currentResource && context.getCurrentResource() instanceof com.sluice.api.pipeline.FileMediaResource fmr) {
                // upload and create derived asset
                try (java.io.FileInputStream fis = new java.io.FileInputStream(fmr.getFile())) {
                    String derivedUrl = storageService.uploadFile(
                            fmr.getFile().getName(),
                            fmr.getContentType(),
                            fis,
                            fmr.getSize()
                    );
                    
                    Asset derived = new Asset(
                            UUID.randomUUID(),
                            fmr.getFile().getName(),
                            fmr.getSize(),
                            fmr.getContentType(),
                            derivedUrl,
                            Asset.UploadStatus.COMPLETED,
                            Instant.now(),
                            job.getProjectId()
                    );
                    derived.setParentAsset(asset);
                    derived.setProducingJobId(job.getId());
                    assetRepository.save(derived);
                    System.out.println("Created derived asset " + derived.getId() + " from job " + job.getId());
                }
            }

            // Update status to COMPLETED
            jobService.updateJobStatusSystem(job.getId(), JobStatus.COMPLETED);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            System.out.println("Job " + message.getJobId() + " overridden by another process (Optimistic Lock). Aborting.");
        } catch (Exception e) {
            System.err.println("Job " + message.getJobId() + " attempt failed: " + e.getMessage());
            try {
                jobService.requeueRunningJob(message.getJobId());
            } catch (Exception updateEx) {
                System.err.println("Could not reset job for retry: " + updateEx.getMessage());
            }
            throw e;
        } finally {
            com.sluice.api.pipeline.MediaResource resourceToClean = processingContext != null
                    ? processingContext.getCurrentResource()
                    : finalResource;
            if (resourceToClean != null) {
                resourceToClean.cleanup();
            }
        }
    }
}

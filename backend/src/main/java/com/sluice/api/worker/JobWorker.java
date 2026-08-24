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
import com.sluice.api.step.service.StepRunService;
import com.sluice.api.pipeline.StepExecutionListener;
import com.sluice.api.pipeline.ConfiguredStep;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.sluice.api.observability.SluiceMetrics;

@Service
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobService jobService;
    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final PipelineEngine pipelineEngine;
    private final PipelineVersionRepository pipelineVersionRepository;
    private final PipelineResolver pipelineResolver;
    private final StepRunService stepRuns;
    private final OutputReconciliationService outputs;
    private final com.sluice.api.governance.GovernanceDecisionService governanceDecisions;
    private final SluiceMetrics metrics;

    @org.springframework.beans.factory.annotation.Autowired
    public JobWorker(JobService jobService, 
                     AssetRepository assetRepository, 
                     StorageService storageService,
                     PipelineEngine pipelineEngine,
                     PipelineVersionRepository pipelineVersionRepository,
                     PipelineResolver pipelineResolver, StepRunService stepRuns,
                     OutputReconciliationService outputs,
                     com.sluice.api.governance.GovernanceDecisionService governanceDecisions,
                     SluiceMetrics metrics) {
        this.jobService = jobService;
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.pipelineEngine = pipelineEngine;
        this.pipelineVersionRepository = pipelineVersionRepository;
        this.pipelineResolver = pipelineResolver;
        this.stepRuns = stepRuns;
        this.outputs = outputs;
        this.governanceDecisions = governanceDecisions;
        this.metrics = metrics;
    }

    public JobWorker(JobService jobService, AssetRepository assetRepository, StorageService storageService,
                     PipelineEngine pipelineEngine, PipelineVersionRepository pipelineVersionRepository,
                     PipelineResolver pipelineResolver, StepRunService stepRuns,
                     OutputReconciliationService outputs) {
        this(jobService, assetRepository, storageService, pipelineEngine, pipelineVersionRepository,
                pipelineResolver, stepRuns, outputs, null, null);
    }

    public JobWorker(JobService jobService, AssetRepository assetRepository, StorageService storageService,
                     PipelineEngine pipelineEngine, PipelineVersionRepository pipelineVersionRepository,
                     PipelineResolver pipelineResolver, StepRunService stepRuns,
                     OutputReconciliationService outputs,
                     com.sluice.api.governance.GovernanceDecisionService governanceDecisions) {
        this(jobService, assetRepository, storageService, pipelineEngine, pipelineVersionRepository,
                pipelineResolver, stepRuns, outputs, governanceDecisions, null);
    }

    public JobWorker(JobService jobService, AssetRepository assetRepository, StorageService storageService,
                     PipelineEngine pipelineEngine, PipelineVersionRepository pipelineVersionRepository,
                     PipelineResolver pipelineResolver) {
        this(jobService, assetRepository, storageService, pipelineEngine, pipelineVersionRepository,
                pipelineResolver, null, null, null);
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void processJob(JobMessage message) throws Exception {
        if (message.getRequestId() != null) MDC.put("requestId", message.getRequestId());
        com.sluice.api.pipeline.MediaResource finalResource = null;
        ProcessingContext processingContext = null;
        Job claimedJob = null;
        try {
            // Fetch current job state
            Job job = jobService.getJobSystem(message.getJobId())
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + message.getJobId()));

            if (job.getStatus() != JobStatus.QUEUED) {
                log.info("job_skipped jobId={} status={} reason=duplicate_delivery", job.getId(), job.getStatus());
                return;
            }

            if (!job.getAssetId().equals(message.getAssetId())) {
                throw new PermanentProcessingException("queue_asset_mismatch",
                        "Queued asset does not match the durable run input");
            }

            log.info("job_processing_started jobId={} assetId={}", job.getId(), job.getAssetId());

            // Atomically claim QUEUED -> RUNNING so duplicate deliveries cannot
            // execute the same job in parallel.
            job = jobService.claimQueuedJob(message.getJobId()).orElse(null);
            if (job == null) {
                log.info("job_claim_skipped jobId={} reason=claimed_by_other_worker", message.getJobId());
                return;
            }
            claimedJob = job;
            if (metrics != null) metrics.job("RUNNING");

            // Fetch the asset
            Asset asset = assetRepository.findByIdAndProjectId(job.getAssetId(), job.getProjectId())
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
            
            AtomicReference<String> finalOutputStep = new AtomicReference<>();
            if (stepRuns == null) {
                pipelineEngine.execute(pipeline, context);
            } else {
                int attemptNumber = job.getRetryCount() + 1;
                pipelineEngine.execute(pipeline, context, new StepExecutionListener() {
                    @Override
                    public void beforeStep(ConfiguredStep step, com.sluice.api.pipeline.MediaResource input) {
                        stepRuns.start(message.getJobId(), step.getId(), attemptNumber, input);
                    }

                    @Override
                    public void afterStep(ConfiguredStep step, com.sluice.api.pipeline.MediaResource output,
                                          java.util.Map<String, Object> metadata, boolean resourceChanged) {
                        stepRuns.complete(message.getJobId(), step.getId(), output, metadata);
                        if (metrics != null) metrics.step(step.getProcessor().getMetadata().name(), "COMPLETED");
                        if (governanceDecisions != null && metadata.containsKey(
                                com.sluice.api.pipeline.processor.ContentSafetyProcessor.DECISION_FACT)) {
                            governanceDecisions.persist(message.getJobId(), step.getId(), metadata);
                        }
                        if (resourceChanged) finalOutputStep.set(step.getId());
                    }

                    @Override
                    public void onFailure(ConfiguredStep step, Exception exception) {
                        stepRuns.fail(message.getJobId(), step.getId(), errorCode(exception),
                                "Processor execution failed");
                    }
                });
            }
            finalResource = context.getCurrentResource();

            String governanceDecision = (String) context.getAttributes().get(
                    com.sluice.api.pipeline.processor.ContentSafetyProcessor.DECISION_FACT);
            if ("REVIEW".equals(governanceDecision)) {
                if (metrics != null) metrics.governance("REVIEW");
                jobService.requireReviewSystem(job.getId(), asset.getSize());
                return;
            }
            if ("BLOCK".equals(governanceDecision)) {
                if (metrics != null) metrics.governance("BLOCK");
                jobService.failJobSystem(job.getId(), "governance_blocked", "Content was blocked by governance policy");
                return;
            }

            // Save new derived assets if the pipeline created a new resource
            Asset derived = null;
            if (context.getCurrentResource() != currentResource && context.getCurrentResource() instanceof com.sluice.api.pipeline.FileMediaResource fmr) {
                if (outputs != null) {
                    derived = outputs.reconcile(job, asset, fmr);
                } else {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(fmr.getFile())) {
                        String derivedUrl = storageService.uploadFile(fmr.getFile().getName(), fmr.getContentType(),
                                fis, fmr.getSize());
                        derived = new Asset(UUID.randomUUID(), fmr.getFile().getName(), fmr.getSize(),
                                fmr.getContentType(), derivedUrl, Asset.UploadStatus.COMPLETED, Instant.now(),
                                job.getProjectId());
                        derived.setParentAsset(asset);
                        derived.setProducingJobId(job.getId());
                        assetRepository.save(derived);
                    }
                }
                if (stepRuns != null && finalOutputStep.get() != null) {
                    stepRuns.attachOutput(job.getId(), finalOutputStep.get(), derived.getId());
                }
            }

            // Update status to COMPLETED
            if (stepRuns == null) {
                jobService.updateJobStatusSystem(job.getId(), JobStatus.COMPLETED);
            } else {
                long outputBytes = context.getCurrentResource() == null ? 0 : context.getCurrentResource().getSize();
                jobService.completeJobSystem(job.getId(), asset.getSize(), outputBytes,
                        derived == null ? null : derived.getId());
            }
            if (metrics != null) metrics.job("COMPLETED");
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.info("job_claim_lost jobId={} reason=optimistic_lock", message.getJobId());
        } catch (Exception e) {
            log.error("job_attempt_failed jobId={} errorCode={}", message.getJobId(), errorCode(e), e);
            if (claimedJob == null) throw e;
            if (metrics != null) metrics.job("FAILED");
            if (stepRuns == null) {
                jobService.requeueRunningJob(message.getJobId());
                throw e;
            }
            if (isPermanent(e)) {
                jobService.failJobSystem(message.getJobId(), errorCode(e), "Processing failed permanently");
            } else if (claimedJob.getRetryCount() + 1 >= 3) {
                jobService.failJobSystem(message.getJobId(), "retry_exhausted", "Processing retry limit was reached");
            } else {
                long seconds = 1L << claimedJob.getRetryCount();
                jobService.scheduleRetry(message.getJobId(), errorCode(e), "Processing will be retried",
                        Duration.ofSeconds(seconds));
            }
        } finally {
            com.sluice.api.pipeline.MediaResource resourceToClean = processingContext != null
                    ? processingContext.getCurrentResource()
                    : finalResource;
            if (resourceToClean != null) {
                resourceToClean.cleanup();
            }
            if (message.getRequestId() != null) MDC.remove("requestId");
        }
    }

    private boolean isPermanent(Exception exception) {
        return exception instanceof PermanentProcessingException || exception instanceof IllegalArgumentException;
    }

    private static String errorCode(Exception exception) {
        if (exception instanceof PermanentProcessingException permanent) return permanent.getCode();
        if (exception instanceof IllegalArgumentException) return "invalid_processing_input";
        if (exception instanceof java.io.IOException) return "storage_unavailable";
        return "processor_transient_failure";
    }
}

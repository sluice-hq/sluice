package com.sluice.api.job.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.job.repository.RunAttemptRepository;
import com.sluice.api.job.domain.RunAttempt;
import com.sluice.api.outbox.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.sluice.api.pipeline.MediaTypeMatcher;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.sluice.api.pipeline.service.PipelineService pipelineService;
    private final AssetRepository assetRepository;
    private final RunAttemptRepository attempts;
    private final OutboxService outbox;

    @org.springframework.beans.factory.annotation.Autowired
    public JobService(JobRepository jobRepository, ApplicationEventPublisher eventPublisher,
                      com.sluice.api.pipeline.service.PipelineService pipelineService,
                      AssetRepository assetRepository, RunAttemptRepository attempts, OutboxService outbox) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
        this.pipelineService = pipelineService;
        this.assetRepository = assetRepository;
        this.attempts = attempts;
        this.outbox = outbox;
    }

    public JobService(JobRepository jobRepository, ApplicationEventPublisher eventPublisher,
                      com.sluice.api.pipeline.service.PipelineService pipelineService,
                      AssetRepository assetRepository) {
        this(jobRepository, eventPublisher, pipelineService, assetRepository, null, null);
    }

    @Transactional(readOnly = true)
    public Page<Job> getJobs(ProjectContext context, Pageable pageable) {
        return jobRepository.findAllByProjectId(context.getProjectId(), pageable);
    }

    @Transactional
    public Job createJob(UUID assetId, UUID pipelineId, ProjectContext context) {
        com.sluice.api.pipeline.domain.PipelineVersion version = pipelineService.getLatestPublishedVersion(pipelineId, context)
                .orElseThrow(() -> new IllegalArgumentException("No published version found for pipeline: " + pipelineId));

        return createJobForVersion(assetId, version, context);
    }

    @Transactional
    public Job createJobForVersion(UUID assetId, com.sluice.api.pipeline.domain.PipelineVersion version,
                                   ProjectContext context) {
        return createJobForVersion(UUID.randomUUID(), assetId, version, context);
    }

    @Transactional
    public Job createJobForVersion(UUID jobId, UUID assetId,
                                   com.sluice.api.pipeline.domain.PipelineVersion version,
                                   ProjectContext context) {
        return createJobForVersion(jobId, assetId, version, null, context);
    }

    @Transactional
    public Job createJobForVersion(UUID jobId, UUID assetId,
                                   com.sluice.api.pipeline.domain.PipelineVersion version,
                                   UUID webhookEndpointId, ProjectContext context) {
        Instant now = Instant.now();
        Job job = new Job(jobId, assetId, JobStatus.QUEUED, now, now, context.getProjectId());

        Asset asset = assetRepository.findByIdAndProjectId(assetId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        if (asset.getUploadStatus() != Asset.UploadStatus.COMPLETED) {
            throw new IllegalStateException("Asset upload must be completed before processing");
        }
        if (!MediaTypeMatcher.matches(asset.getContentType(), version.getExpectedInputMimeType())) {
            throw new IllegalArgumentException("Asset content type is not compatible with the pipeline input");
        }
        
        job.setPipelineVersionId(version.getId());
        job.setWebhookEndpointId(webhookEndpointId);
        
        Job savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(this, savedJob.getId(), savedJob.getStatus()));
        return savedJob;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Job> getJob(UUID id, ProjectContext context) {
        return jobRepository.findByIdAndProjectId(id, context.getProjectId());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Job> getJobSystem(UUID id) {
        return jobRepository.findById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public Job updateJobStatus(UUID id, JobStatus newStatus, ProjectContext context) {
        Job job = jobRepository.findByIdAndProjectId(id, context.getProjectId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + id));
        requireTransition(job.getStatus(), newStatus);
        job.setStatus(newStatus);
        Job savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(this, savedJob.getId(), savedJob.getStatus()));
        return savedJob;
    }

    @org.springframework.transaction.annotation.Transactional
    public Job updateJobStatusSystem(UUID id, JobStatus newStatus) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found: " + id));
        requireTransition(job.getStatus(), newStatus);
        job.setStatus(newStatus);
        Job savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(this, savedJob.getId(), savedJob.getStatus()));
        return savedJob;
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<Job> claimQueuedJob(UUID id) {
        Instant started = Instant.now();
        if (jobRepository.claimQueuedJob(id, started, JobStatus.RUNNING) == 0) {
            return Optional.empty();
        }
        Job claimed = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed job disappeared: " + id));
        if (attempts != null) {
            attempts.save(new RunAttempt(UUID.randomUUID(), claimed.getId(), claimed.getRetryCount() + 1, started));
        }
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(
                this, claimed.getId(), claimed.getStatus()));
        return Optional.of(claimed);
    }

    @org.springframework.transaction.annotation.Transactional
    public void requeueRunningJob(UUID id) {
        scheduleRetry(id, "processing_transient", "Processing will be retried", java.time.Duration.ZERO);
    }

    @Transactional
    public void scheduleRetry(UUID id, String code, String safeMessage, java.time.Duration delay) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Run not found"));
        requireState(job, JobStatus.RUNNING);
        job.setStatus(JobStatus.RETRY_WAIT);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setNextRetryAt(Instant.now().plus(delay));
        job.setErrorCode(code);
        job.setErrorMessage(safe(safeMessage));
        completeAttempt(job, "RETRY_WAIT", code, safeMessage, true);
        publish(jobRepository.save(job));
    }

    @Transactional
    public Job completeJobSystem(UUID id, long inputBytes, long outputBytes, UUID outputAssetId) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Run not found"));
        if (job.getStatus() == JobStatus.COMPLETED) return job;
        requireState(job, JobStatus.RUNNING);
        job.setInputBytes(inputBytes);
        job.setOutputBytes(outputBytes);
        long saved = Math.max(0, inputBytes - outputBytes);
        job.setBytesSaved(saved);
        job.setCompressionRatio(inputBytes == 0 ? java.math.BigDecimal.ZERO
                : java.math.BigDecimal.valueOf(outputBytes)
                .divide(java.math.BigDecimal.valueOf(inputBytes), 6, java.math.RoundingMode.HALF_UP));
        job.setProcessingCompletedAt(Instant.now());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setStatus(JobStatus.COMPLETED);
        completeAttempt(job, "COMPLETED", null, null, false);
        Job savedJob = jobRepository.save(job);
        if (outbox != null) outbox.createTerminalEvent(savedJob, outputAssetId);
        publish(savedJob);
        return savedJob;
    }

    @Transactional
    public Job requireReviewSystem(UUID id, long inputBytes) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Run not found"));
        if (job.getStatus() == JobStatus.REVIEW_REQUIRED) return job;
        requireState(job, JobStatus.RUNNING);
        job.setInputBytes(inputBytes);
        job.setProcessingCompletedAt(Instant.now());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setStatus(JobStatus.REVIEW_REQUIRED);
        completeAttempt(job, "REVIEW_REQUIRED", null, null, false);
        Job savedJob = jobRepository.save(job);
        if (outbox != null) outbox.createTerminalEvent(savedJob, null);
        publish(savedJob);
        return savedJob;
    }

    @Transactional
    public Job failJobSystem(UUID id, String code, String safeMessage) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Run not found"));
        if (job.isTerminal()) return job;
        if (job.getStatus() != JobStatus.RUNNING && job.getStatus() != JobStatus.QUEUED
                && job.getStatus() != JobStatus.RETRY_WAIT) {
            throw new IllegalStateException("Run cannot fail from " + job.getStatus());
        }
        job.setErrorCode(code);
        job.setErrorMessage(safe(safeMessage));
        job.setProcessingCompletedAt(Instant.now());
        job.setNextRetryAt(null);
        job.setStatus(JobStatus.FAILED);
        completeAttempt(job, "FAILED", code, safeMessage, false);
        Job savedJob = jobRepository.save(job);
        if (outbox != null) outbox.createTerminalEvent(savedJob, null);
        publish(savedJob);
        return savedJob;
    }

    @Transactional
    public int requeueDueRetries(int batchSize) {
        java.util.List<Job> due = jobRepository.findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                JobStatus.RETRY_WAIT, Instant.now(), org.springframework.data.domain.PageRequest.of(0, batchSize));
        for (Job job : due) {
            job.setStatus(JobStatus.QUEUED);
            job.setQueuedAt(Instant.now());
            job.setNextRetryAt(null);
            Job saved = jobRepository.save(job);
            if (outbox != null) outbox.createRunQueuedEvent(saved);
            publish(saved);
        }
        return due.size();
    }

    private void completeAttempt(Job job, String status, String code, String message, boolean transientFailure) {
        if (attempts == null) return;
        attempts.findByJobIdAndAttemptNumber(job.getId(), job.getRetryCount() + (transientFailure ? 0 : 1))
                .or(() -> attempts.findByJobIdAndAttemptNumber(job.getId(), Math.max(1, job.getRetryCount())))
                .ifPresent(attempt -> {
                    attempt.complete(status, code, safe(message), transientFailure);
                    attempts.save(attempt);
                });
    }

    private void requireState(Job job, JobStatus expected) {
        if (job.getStatus() != expected) throw new IllegalStateException(
                "Run transition requires " + expected + " but was " + job.getStatus());
    }

    private void requireTransition(JobStatus current, JobStatus next) {
        boolean allowed = switch (current) {
            case QUEUED -> next == JobStatus.RUNNING || next == JobStatus.FAILED;
            case RUNNING -> next == JobStatus.COMPLETED || next == JobStatus.RETRY_WAIT
                    || next == JobStatus.REVIEW_REQUIRED || next == JobStatus.FAILED;
            case RETRY_WAIT -> next == JobStatus.QUEUED || next == JobStatus.FAILED;
            case COMPLETED, FAILED, REVIEW_REQUIRED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid run transition: " + current + " -> " + next);
    }

    private String safe(String message) {
        return message == null ? null : message.substring(0, Math.min(500, message.length()));
    }

    private void publish(Job job) {
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(
                this, job.getId(), job.getStatus()));
    }
}

package com.sluice.api.job.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
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

    public JobService(JobRepository jobRepository, ApplicationEventPublisher eventPublisher,
                      com.sluice.api.pipeline.service.PipelineService pipelineService,
                      AssetRepository assetRepository) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
        this.pipelineService = pipelineService;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public Page<Job> getJobs(ProjectContext context, Pageable pageable) {
        return jobRepository.findAllByProjectId(context.getProjectId(), pageable);
    }

    @Transactional
    public Job createJob(UUID assetId, UUID pipelineId, ProjectContext context) {
        Instant now = Instant.now();
        Job job = new Job(UUID.randomUUID(), assetId, JobStatus.QUEUED, now, now, context.getProjectId());
        
        com.sluice.api.pipeline.domain.PipelineVersion version = pipelineService.getLatestPublishedVersion(pipelineId, context)
                .orElseThrow(() -> new IllegalArgumentException("No published version found for pipeline: " + pipelineId));

        Asset asset = assetRepository.findByIdAndProjectId(assetId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        if (asset.getUploadStatus() != Asset.UploadStatus.COMPLETED) {
            throw new IllegalStateException("Asset upload must be completed before processing");
        }
        if (!MediaTypeMatcher.matches(asset.getContentType(), version.getExpectedInputMimeType())) {
            throw new IllegalArgumentException("Asset content type is not compatible with the pipeline input");
        }
        
        job.setPipelineVersionId(version.getId());
        
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
        job.setStatus(newStatus);
        Job savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(this, savedJob.getId(), savedJob.getStatus()));
        return savedJob;
    }

    @org.springframework.transaction.annotation.Transactional
    public Job updateJobStatusSystem(UUID id, JobStatus newStatus) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found: " + id));
        job.setStatus(newStatus);
        Job savedJob = jobRepository.save(job);
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(this, savedJob.getId(), savedJob.getStatus()));
        return savedJob;
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<Job> claimQueuedJob(UUID id) {
        if (jobRepository.claimQueuedJob(id, Instant.now(), JobStatus.RUNNING) == 0) {
            return Optional.empty();
        }
        Job claimed = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Claimed job disappeared: " + id));
        eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(
                this, claimed.getId(), claimed.getStatus()));
        return Optional.of(claimed);
    }

    @org.springframework.transaction.annotation.Transactional
    public void requeueRunningJob(UUID id) {
        jobRepository.findById(id).ifPresent(job -> {
            if (job.getStatus() == JobStatus.RUNNING) {
                job.setStatus(JobStatus.QUEUED);
                Job savedJob = jobRepository.save(job);
                eventPublisher.publishEvent(new com.sluice.api.job.event.JobStatusChangedEvent(
                        this, savedJob.getId(), savedJob.getStatus()));
            }
        });
    }
}

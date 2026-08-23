package com.sluice.api.run.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.service.OutboxService;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import com.sluice.api.pipeline.service.PipelineService;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.repository.StepRunRepository;
import com.sluice.api.run.dto.CreateRunRequest;
import com.sluice.api.run.dto.RunResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunService {
    private final JobService jobs;
    private final JobRepository jobRepository;
    private final AssetRepository assets;
    private final PipelineService pipelines;
    private final PipelineVersionRepository versions;
    private final StepRunRepository steps;
    private final IdempotencyService idempotency;
    private final OutboxService outbox;

    public RunService(JobService jobs, JobRepository jobRepository, AssetRepository assets,
                      PipelineService pipelines, PipelineVersionRepository versions,
                      StepRunRepository steps, IdempotencyService idempotency, OutboxService outbox) {
        this.jobs = jobs;
        this.jobRepository = jobRepository;
        this.assets = assets;
        this.pipelines = pipelines;
        this.versions = versions;
        this.steps = steps;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    @Transactional
    public Job create(CreateRunRequest request, String idempotencyKey, ProjectContext context) {
        validate(request, idempotencyKey);
        String fingerprint = idempotency.hash(fingerprint(request));
        UUID requestedRunId = UUID.randomUUID();
        IdempotencyRecord claim = idempotency.claim(context.getProjectId(), IdempotencyService.RUN_CREATE,
                idempotencyKey, fingerprint, requestedRunId);
        if (claim != null && !claim.getResourceId().equals(requestedRunId)) {
            return jobRepository.findByIdAndProjectId(claim.getResourceId(), context.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("Idempotent run resource no longer exists"));
        }

        PipelineVersion version = pipelines.resolvePublishedVersion(
                request.pipeline(), request.alias(), request.version(), context);
        Job job = jobs.createJobForVersion(requestedRunId, request.inputAssetId(), version, context);
        createPlannedSteps(job, version.getDefinition());
        OutboxEvent event = outbox.createRunQueuedEvent(job);
        outbox.publishAfterCommit(event, new JobMessage(job.getId(), job.getAssetId()));
        return job;
    }

    @Transactional(readOnly = true)
    public Page<RunResponse> list(ProjectContext context, Pageable pageable) {
        return jobRepository.findAllByProjectId(context.getProjectId(), pageable).map(job -> toResponse(job, context));
    }

    @Transactional(readOnly = true)
    public Optional<RunResponse> get(UUID id, ProjectContext context) {
        return jobRepository.findByIdAndProjectId(id, context.getProjectId()).map(job -> toResponse(job, context));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> outputs(UUID id, ProjectContext context) {
        Job job = jobRepository.findByIdAndProjectId(id, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
        return assets.findByProducingJobIdAndProjectId(job.getId(), context.getProjectId()).stream()
                .map(asset -> new AssetResponse(asset.getId(), asset.getFilename(), asset.getSize(),
                        asset.getContentType(), asset.getStorageUrl(), asset.getUploadStatus().name(), asset.getCreatedAt()))
                .toList();
    }

    private RunResponse toResponse(Job job, ProjectContext context) {
        PipelineVersion version = versions.findById(job.getPipelineVersionId())
                .orElseThrow(() -> new IllegalStateException("Run pipeline version no longer exists"));
        List<RunResponse.StepResponse> plannedSteps = steps.findByJobIdOrderByCreatedAtAsc(job.getId()).stream()
                .map(RunResponse.StepResponse::from).toList();
        List<AssetResponse> outputs = assets.findByProducingJobIdAndProjectId(job.getId(), context.getProjectId()).stream()
                .map(asset -> new AssetResponse(asset.getId(), asset.getFilename(), asset.getSize(),
                        asset.getContentType(), asset.getStorageUrl(), asset.getUploadStatus().name(), asset.getCreatedAt()))
                .toList();
        return new RunResponse(job.getId(), job.getStatus().name(),
                new RunResponse.PipelineReference(version.getPipeline().getSlug(), version.getVersionNumber()),
                job.getAssetId(), plannedSteps, outputs, job.getCreatedAt(), job.getUpdatedAt());
    }

    private void createPlannedSteps(Job job, JsonNode definition) {
        if (definition == null || !definition.path("steps").isArray()) return;
        List<StepRun> planned = new ArrayList<>();
        for (JsonNode step : definition.path("steps")) {
            String processor = step.path("processor").asText(null);
            String processorVersion = step.path("version").asText(null);
            String stepId = step.path("id").asText(null);
            if (processor == null || processorVersion == null || stepId == null) {
                throw new IllegalArgumentException("Published pipeline contains an invalid step");
            }
            planned.add(new StepRun(UUID.randomUUID(), job.getId(), stepId, processor, processorVersion, "PENDING"));
        }
        steps.saveAll(planned);
    }

    private String fingerprint(CreateRunRequest request) {
        return String.join("|", request.pipeline().trim(),
                request.alias() == null ? "" : request.alias().trim(),
                request.version() == null ? "" : request.version().toString(),
                request.inputAssetId().toString());
    }

    private void validate(CreateRunRequest request, String idempotencyKey) {
        if (request == null || request.pipeline() == null || request.pipeline().isBlank()) {
            throw new IllegalArgumentException("Pipeline slug is required");
        }
        if (request.inputAssetId() == null) throw new IllegalArgumentException("inputAssetId is required");
        if (request.version() != null && request.version() <= 0) throw new IllegalArgumentException("Pipeline version must be positive");
        if (idempotencyKey != null && idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key cannot be blank");
    }
}

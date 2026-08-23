package com.sluice.api.run.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.service.IdempotencyService;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.job.service.JobService;
import com.sluice.api.outbox.domain.OutboxEvent;
import com.sluice.api.outbox.service.OutboxService;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import com.sluice.api.pipeline.service.PipelineService;
import com.sluice.api.step.repository.StepRunRepository;
import com.sluice.api.run.dto.CreateRunRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class RunServiceTest {
    private final UUID projectId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final ProjectContext context = new ProjectContext(projectId, null, true);

    @Test
    void repeatedRequestWithSameKeyReturnsTheOriginalRun() throws Exception {
        IdempotencyService idempotency = mock(IdempotencyService.class);
        JobRepository jobsRepository = mock(JobRepository.class);
        Job original = job();
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(eq(projectId), eq(IdempotencyService.RUN_CREATE), eq("request-1"), eq("hash"), any()))
                .thenReturn(new IdempotencyRecord(UUID.randomUUID(), projectId,
                        IdempotencyService.RUN_CREATE, "request-1", "hash", jobId));
        when(jobsRepository.findByIdAndProjectId(jobId, projectId)).thenReturn(Optional.of(original));

        JobService jobService = mock(JobService.class);
        PipelineService pipelines = mock(PipelineService.class);
        StepRunRepository steps = mock(StepRunRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        RunService runs = new RunService(jobService, jobsRepository, mock(AssetRepository.class), pipelines,
                mock(PipelineVersionRepository.class), steps, idempotency, outbox);
        Job result = runs.create(new CreateRunRequest("product-images", null, null, assetId), "request-1", context);

        assertEquals(jobId, result.getId());
        verifyNoInteractions(jobService, pipelines, steps, outbox);
    }

    @Test
    void conflictingKeyReuseIsRejected() {
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(eq(projectId), eq(IdempotencyService.RUN_CREATE), eq("request-1"), eq("hash"), any()))
                .thenThrow(new com.sluice.api.idempotency.service.IdempotencyConflictException());

        assertThrows(com.sluice.api.idempotency.service.IdempotencyConflictException.class,
                () -> service(mock(JobService.class), mock(JobRepository.class), idempotency)
                        .create(new CreateRunRequest("product-images", null, null, assetId), "request-1", context));
    }

    @Test
    void createsPlannedStepsAndAnOutboxEventForAReusableAsset() throws Exception {
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(idempotency.hash(any())).thenReturn("hash");
        when(idempotency.claim(any(), any(), any(), any(), any())).thenReturn(null);

        Pipeline pipeline = new Pipeline(UUID.randomUUID(), "product-images", "Product images", null, projectId);
        PipelineVersion version = new PipelineVersion(UUID.randomUUID(), pipeline, 3, "PUBLISHED", "image/*",
                new ObjectMapper().readTree("""
                        {"steps":[{"id":"resize","processor":"resize","version":"1.0.0"}]}
                        """));
        PipelineService pipelines = mock(PipelineService.class);
        when(pipelines.resolvePublishedVersion("product-images", null, null, context)).thenReturn(version);

        JobService jobService = mock(JobService.class);
        when(jobService.createJobForVersion(any(), eq(assetId), eq(version), eq(context))).thenReturn(job());
        StepRunRepository steps = mock(StepRunRepository.class);
        OutboxService outbox = mock(OutboxService.class);
        when(outbox.createRunQueuedEvent(any())).thenReturn(new OutboxEvent(UUID.randomUUID(), "run.queued", "JOB", jobId, "{}"));

        new RunService(jobService, mock(JobRepository.class), mock(AssetRepository.class), pipelines,
                mock(PipelineVersionRepository.class), steps, idempotency, outbox)
                .create(new CreateRunRequest("product-images", null, null, assetId), "request-1", context);

        ArgumentCaptor<Iterable< com.sluice.api.step.domain.StepRun>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(steps).saveAll(captured.capture());
        var iterator = captured.getValue().iterator();
        org.junit.jupiter.api.Assertions.assertTrue(iterator.hasNext());
        var planned = iterator.next();
        assertEquals("resize", planned.getStepId());
        assertEquals("PENDING", planned.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(iterator.hasNext());
        verify(outbox).createRunQueuedEvent(any(Job.class));
        verify(outbox).publishAfterCommit(any(OutboxEvent.class), any());
    }

    private RunService service(JobService jobs, JobRepository repository, IdempotencyService idempotency) {
        return new RunService(jobs, repository, mock(AssetRepository.class), mockPipeline(),
                mock(PipelineVersionRepository.class), mock(StepRunRepository.class), idempotency,
                mock(OutboxService.class));
    }

    private PipelineService mockPipeline() { return mock(PipelineService.class); }

    private Job job() {
        Instant now = Instant.now();
        return new Job(jobId, assetId, JobStatus.QUEUED, now, now, projectId);
    }
}

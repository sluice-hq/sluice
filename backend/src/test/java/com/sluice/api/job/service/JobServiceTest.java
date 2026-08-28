package com.sluice.api.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.service.PipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    @Test
    void createsJobForCompletedAssetCompatibleWithPublishedPipeline() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.png", 10, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        PipelineVersion version = new PipelineVersion(versionId, null, 1, "PUBLISHED", "image/*",
                new ObjectMapper().readTree("{\"steps\":[]}"));

        JobRepository jobs = mock(JobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        PipelineService pipelines = mock(PipelineService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));
        when(pipelines.getLatestPublishedVersion(pipelineId, context)).thenReturn(Optional.of(version));
        when(jobs.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job created = new JobService(jobs, events, pipelines, assets).createJob(assetId, pipelineId, context);

        assertEquals(versionId, created.getPipelineVersionId());
        verify(jobs).save(created);
        verify(events).publishEvent(any(com.sluice.api.job.event.JobStatusChangedEvent.class));
    }

    @Test
    void rejectsAssetWhoseContentTypeDoesNotMatchPipeline() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.pdf", 10, "application/pdf", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        PipelineVersion version = new PipelineVersion(UUID.randomUUID(), null, 1, "PUBLISHED", "image/*",
                new ObjectMapper().readTree("{\"steps\":[]}"));

        JobRepository jobs = mock(JobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        PipelineService pipelines = mock(PipelineService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));
        when(pipelines.getLatestPublishedVersion(pipelineId, context)).thenReturn(Optional.of(version));

        assertThrows(IllegalArgumentException.class,
                () -> new JobService(jobs, events, pipelines, assets).createJob(assetId, pipelineId, context));

        verify(jobs, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void acceptsAnyMimePatternFromTheResolvedInputContract() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.pdf", 10, "application/pdf", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        PipelineVersion version = publishedVersion("image/png", """
                {"kind":"document","mimeTypes":["image/png","application/pdf"],"maxBytes":100}
                """);
        JobRepository jobs = mock(JobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));
        when(jobs.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job created = new JobService(jobs, mock(ApplicationEventPublisher.class), mock(PipelineService.class), assets)
                .createJobForVersion(assetId, version, context);

        assertEquals(version.getId(), created.getPipelineVersionId());
    }

    @Test
    void rejectsAssetAboveResolvedInputByteLimit() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.png", 101, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
        PipelineVersion version = publishedVersion("image/png", """
                {"kind":"image","mimeTypes":["image/png"],"maxBytes":100}
                """);
        JobRepository jobs = mock(JobRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));

        assertThrows(IllegalArgumentException.class,
                () -> new JobService(jobs, mock(ApplicationEventPublisher.class), mock(PipelineService.class), assets)
                        .createJobForVersion(assetId, version, context));
        verify(jobs, never()).save(any());
    }

    private PipelineVersion publishedVersion(String expectedMimeType, String resolvedContract) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PipelineVersion version = new PipelineVersion(UUID.randomUUID(), null, 1, "DRAFT", expectedMimeType,
                mapper.readTree("{\"steps\":[]}"));
        version.publish(mapper.createObjectNode(), mapper.readTree(resolvedContract), mapper.createObjectNode());
        return version;
    }
}

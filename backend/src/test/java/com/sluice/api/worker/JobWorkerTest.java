package com.sluice.api.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.pipeline.PipelineEngine;
import com.sluice.api.pipeline.PipelineResolver;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class JobWorkerTest {

    @Test
    void processingFailureRequeuesRethrowsAndCleansResource() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();

        Job queued = new Job(jobId, assetId, JobStatus.QUEUED, now, now, projectId);
        Job running = new Job(jobId, assetId, JobStatus.RUNNING, now, now, projectId);
        running.setPipelineVersionId(versionId);
        Asset asset = new Asset(assetId, "input.png", 1, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, now, projectId);
        PipelineVersion version = new PipelineVersion(versionId, null, 1, "PUBLISHED", "image/png",
                new ObjectMapper().readTree("{\"steps\":[]}"));

        JobService jobs = mock(JobService.class);
        AssetRepository assets = mock(AssetRepository.class);
        StorageService storage = mock(StorageService.class);
        PipelineEngine engine = mock(PipelineEngine.class);
        PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
        PipelineResolver resolver = mock(PipelineResolver.class);
        TrackingResource resource = new TrackingResource();

        when(jobs.getJobSystem(jobId)).thenReturn(Optional.of(queued));
        when(jobs.claimQueuedJob(jobId)).thenReturn(Optional.of(running));
        when(assets.findById(assetId)).thenReturn(Optional.of(asset));
        when(storage.downloadFile("blob-url")).thenReturn(resource);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(resolver.resolve(version.getDefinition())).thenReturn(new com.sluice.api.pipeline.Pipeline(List.of()));
        doThrow(new IllegalStateException("processor failed"))
                .when(engine).execute(any(com.sluice.api.pipeline.Pipeline.class), any(ProcessingContext.class));

        JobWorker worker = new JobWorker(jobs, assets, storage, engine, versions, resolver);

        assertThrows(IllegalStateException.class, () -> worker.processJob(new JobMessage(jobId, assetId)));
        verify(jobs).requeueRunningJob(jobId);
        assertTrue(resource.cleaned);
    }

    @Test
    void recordsTheProducingJobOnDerivedAssets() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        Job queued = new Job(jobId, assetId, JobStatus.QUEUED, now, now, projectId);
        Job running = new Job(jobId, assetId, JobStatus.RUNNING, now, now, projectId);
        running.setPipelineVersionId(versionId);
        Asset asset = new Asset(assetId, "input.png", 1, "image/png", "blob-url",
                Asset.UploadStatus.COMPLETED, now, projectId);
        PipelineVersion version = new PipelineVersion(versionId, null, 1, "PUBLISHED", "image/png",
                new ObjectMapper().readTree("{\"steps\":[]}"));

        JobService jobs = mock(JobService.class);
        AssetRepository assets = mock(AssetRepository.class);
        StorageService storage = mock(StorageService.class);
        PipelineEngine engine = mock(PipelineEngine.class);
        PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
        PipelineResolver resolver = mock(PipelineResolver.class);
        TrackingResource input = new TrackingResource();
        java.io.File output = java.nio.file.Files.createTempFile("sluice-output-", ".webp").toFile();
        java.nio.file.Files.writeString(output.toPath(), "output");

        when(jobs.getJobSystem(jobId)).thenReturn(Optional.of(queued));
        when(jobs.claimQueuedJob(jobId)).thenReturn(Optional.of(running));
        when(assets.findById(assetId)).thenReturn(Optional.of(asset));
        when(storage.downloadFile("blob-url")).thenReturn(input);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(resolver.resolve(version.getDefinition())).thenReturn(new com.sluice.api.pipeline.Pipeline(List.of()));
        when(storage.uploadFile(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn("derived-url");
        org.mockito.Mockito.doAnswer(invocation -> {
            ProcessingContext context = invocation.getArgument(1);
            context.setCurrentResource(new com.sluice.api.pipeline.FileMediaResource(output, "image/webp"));
            return null;
        }).when(engine).execute(any(com.sluice.api.pipeline.Pipeline.class), any(ProcessingContext.class));

        new JobWorker(jobs, assets, storage, engine, versions, resolver).processJob(new JobMessage(jobId, assetId));

        ArgumentCaptor<Asset> derived = ArgumentCaptor.forClass(Asset.class);
        verify(assets).save(derived.capture());
        assertEquals(jobId, derived.getValue().getProducingJobId());
        assertEquals(asset, derived.getValue().getParentAsset());
    }

    private static class TrackingResource implements MediaResource {
        private boolean cleaned;

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public void cleanup() {
            cleaned = true;
        }
    }
}

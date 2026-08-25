package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.run.service.RunService;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceTest {

    @Test
    void delegatesLegacyUploadRunCreationToTheDurableRunService() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Asset asset = new Asset(assetId, "input.png", 10, "image/png", "blob-url",
                Asset.UploadStatus.PENDING, now, projectId);
        Job job = new Job(jobId, assetId, JobStatus.QUEUED, now, now, projectId);

        StorageService storage = mock(StorageService.class);
        AssetRepository assets = mock(AssetRepository.class);
        MediaContentVerifier verifier = mock(MediaContentVerifier.class);
        RunService runs = mock(RunService.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));
        when(assets.save(asset)).thenReturn(asset);
        when(storage.fileExists("blob-url")).thenReturn(true);
        when(storage.getFileSize("blob-url")).thenReturn(10L);
        when(runs.createLegacy(assetId, pipelineId, context)).thenReturn(job);

        new AssetService(storage, assets, verifier, runs).completeUpload(assetId, pipelineId, context);

        verify(runs).createLegacy(assetId, pipelineId, context);
    }
}

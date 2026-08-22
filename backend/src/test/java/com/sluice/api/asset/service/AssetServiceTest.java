package com.sluice.api.asset.service;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.JobPublisher;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishesJobOnlyAfterDatabaseCommit() {
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
        JobService jobs = mock(JobService.class);
        JobPublisher publisher = mock(JobPublisher.class);
        when(assets.findByIdAndProjectId(assetId, projectId)).thenReturn(Optional.of(asset));
        when(assets.save(asset)).thenReturn(asset);
        when(storage.fileExists("blob-url")).thenReturn(true);
        when(storage.getFileSize("blob-url")).thenReturn(10L);
        when(jobs.createJob(assetId, pipelineId, context)).thenReturn(job);
        TransactionSynchronizationManager.initSynchronization();

        new AssetService(storage, assets, jobs, publisher).completeUpload(assetId, pipelineId, context);

        verify(publisher, never()).publishJob(org.mockito.ArgumentMatchers.any());
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        verify(publisher).publishJob(argThat((JobMessage message) ->
                message.getJobId().equals(jobId) && message.getAssetId().equals(assetId)));
    }
}

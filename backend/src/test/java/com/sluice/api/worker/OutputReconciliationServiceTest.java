package com.sluice.api.worker;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutputReconciliationServiceTest {
    @Test
    void reusesAnExistingRunOutputAfterAWorkerRestart() throws Exception {
        UUID projectId = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobStatus.RUNNING,
                Instant.now(), Instant.now(), projectId);
        Asset input = asset(job.getAssetId(), projectId, "input");
        Asset existing = asset(UUID.randomUUID(), projectId, "existing");
        existing.setProducingJobId(job.getId());
        AssetRepository assets = mock(AssetRepository.class);
        StorageService storage = mock(StorageService.class);
        when(assets.findByProducingJobIdAndProjectId(job.getId(), projectId)).thenReturn(List.of(existing));
        when(storage.fileExists(existing.getStorageUrl())).thenReturn(true);
        java.io.File file = java.nio.file.Files.createTempFile("reconcile-", ".webp").toFile();

        Asset result = new OutputReconciliationService(assets, storage)
                .reconcile(job, input, new FileMediaResource(file, "image/webp"));

        assertSame(existing, result);
        verify(storage, never()).uploadFileAt(anyString(), anyString(), any(), anyLong());
        verify(assets, never()).upsertProducedOutput(any(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), any(), any(), any(), any());
        java.nio.file.Files.deleteIfExists(file.toPath());
    }

    @Test
    void createsAProjectScopedOutputWithRunAndParentProvenance() throws Exception {
        UUID projectId = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobStatus.RUNNING,
                Instant.now(), Instant.now(), projectId);
        Asset input = asset(job.getAssetId(), projectId, "input");
        Asset persisted = asset(UUID.randomUUID(), projectId, "blob-output");
        persisted.setProducingJobId(job.getId());
        persisted.setParentAsset(input);
        AssetRepository assets = mock(AssetRepository.class);
        StorageService storage = mock(StorageService.class);
        when(assets.findByProducingJobIdAndProjectId(job.getId(), projectId))
                .thenReturn(List.of(), List.of(persisted));
        when(storage.uploadFileAt(anyString(), eq("image/webp"), any(), anyLong()))
                .thenReturn("blob-output");
        when(assets.upsertProducedOutput(any(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any(), any())).thenReturn(1);
        java.io.File file = java.nio.file.Files.createTempFile("reconcile-", ".webp").toFile();
        java.nio.file.Files.writeString(file.toPath(), "bytes");

        Asset result = new OutputReconciliationService(assets, storage)
                .reconcile(job, input, new FileMediaResource(file, "image/webp"));

        assertEquals(job.getId(), result.getProducingJobId());
        assertSame(input, result.getParentAsset());
        assertEquals(projectId, result.getProjectId());
        assertEquals("blob-output", result.getStorageUrl());
        verify(storage).uploadFileAt(eq(job.getId() + "-output.webp"), eq("image/webp"), any(),
                eq(file.length()));
        verify(assets).upsertProducedOutput(any(), eq(job.getId() + "-output.webp"), eq(file.length()),
                eq("image/webp"), eq("blob-output"), eq("COMPLETED"), any(), eq(projectId),
                eq(job.getId()), eq(input.getId()));
        java.nio.file.Files.deleteIfExists(file.toPath());
    }

    @Test
    void concurrentConflictConvergesOnTheSingleDurableOutputAndStableObjectName() throws Exception {
        UUID projectId = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobStatus.RUNNING,
                Instant.now(), Instant.now(), projectId);
        Asset input = asset(job.getAssetId(), projectId, "input");
        Asset winner = asset(UUID.randomUUID(), projectId, "stable-output");
        winner.setProducingJobId(job.getId());
        AssetRepository assets = mock(AssetRepository.class);
        StorageService storage = mock(StorageService.class);
        when(assets.findByProducingJobIdAndProjectId(job.getId(), projectId))
                .thenReturn(List.of(), List.of(winner));
        when(storage.uploadFileAt(anyString(), anyString(), any(), anyLong())).thenReturn("stable-output");
        when(assets.upsertProducedOutput(any(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any(), any())).thenReturn(1);
        java.io.File file = java.nio.file.Files.createTempFile("reconcile-race-", ".webp").toFile();
        java.nio.file.Files.writeString(file.toPath(), "same output bytes");

        Asset result = new OutputReconciliationService(assets, storage)
                .reconcile(job, input, new FileMediaResource(file, "image/webp"));

        assertSame(winner, result);
        verify(storage).uploadFileAt(eq(job.getId() + "-output.webp"), eq("image/webp"), any(), anyLong());
        verify(storage, never()).deleteFile(anyString());
        java.nio.file.Files.deleteIfExists(file.toPath());
    }

    private Asset asset(UUID id, UUID projectId, String storageUrl) {
        return new Asset(id, "asset.png", 5, "image/png", storageUrl,
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId);
    }
}

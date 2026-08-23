package com.sluice.api.worker;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.storage.StorageService;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SluiceIntegrationTest
class OutputReconciliationPersistenceTests {
    @Autowired
    private ProjectRepository projects;
    @Autowired
    private AssetRepository assets;
    @Autowired
    private JobRepository jobs;
    @Autowired
    private OutputReconciliationService reconciliation;
    @Autowired
    private StorageService storage;

    @Test
    void databaseUpsertKeepsOneOutputWhenTheProducingRunConflicts() throws Exception {
        UUID projectId = UUID.randomUUID();
        projects.save(new Project(projectId, "Output boundary", Instant.now()));
        Asset input = assets.save(new Asset(UUID.randomUUID(), "input.png", 5, "image/png", "input",
                Asset.UploadStatus.COMPLETED, Instant.now(), projectId));
        Job job = jobs.save(new Job(UUID.randomUUID(), input.getId(), JobStatus.RUNNING,
                Instant.now(), Instant.now(), projectId));
        when(storage.uploadFileAt(anyString(), anyString(), any(), anyLong())).thenReturn("stable-output");

        java.io.File firstFile = java.nio.file.Files.createTempFile("output-upsert-first-", ".webp").toFile();
        java.io.File secondFile = java.nio.file.Files.createTempFile("output-upsert-second-", ".webp").toFile();
        java.nio.file.Files.writeString(firstFile.toPath(), "first bytes");
        java.nio.file.Files.writeString(secondFile.toPath(), "second bytes");
        try {
            Asset first = reconciliation.reconcile(job, input, new FileMediaResource(firstFile, "image/webp"));
            Asset second = reconciliation.reconcile(job, input, new FileMediaResource(secondFile, "image/webp"));

            var outputs = assets.findByProducingJobIdAndProjectId(job.getId(), projectId);
            assertEquals(1, outputs.size());
            assertEquals(first.getId(), second.getId());
            assertEquals("stable-output", outputs.get(0).getStorageUrl());
            assertEquals(secondFile.length(), outputs.get(0).getSize());
        } finally {
            java.nio.file.Files.deleteIfExists(firstFile.toPath());
            java.nio.file.Files.deleteIfExists(secondFile.toPath());
        }
    }
}

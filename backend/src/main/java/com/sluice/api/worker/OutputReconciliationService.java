package com.sluice.api.worker;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@Service
public class OutputReconciliationService {
    private final AssetRepository assets;
    private final StorageService storage;

    public OutputReconciliationService(AssetRepository assets, StorageService storage) {
        this.assets = assets; this.storage = storage;
    }

    @Transactional
    public Asset reconcile(Job job, Asset input, FileMediaResource output) throws Exception {
        Asset existing = assets.findByProducingJobIdAndProjectId(job.getId(), job.getProjectId()).stream()
                .min(Comparator.comparing(Asset::getCreatedAt)).orElse(null);
        if (existing != null && storage.fileExists(existing.getStorageUrl())) return existing;

        String filename = job.getId() + "-output" + extension(output.getContentType());
        String storageUrl;
        try (FileInputStream stream = new FileInputStream(output.getFile())) {
            storageUrl = storage.uploadFileAt(filename, output.getContentType(), stream, output.getSize());
        }

        UUID outputAssetId = UUID.nameUUIDFromBytes(
                ("sluice-run-output:" + job.getId()).getBytes(StandardCharsets.UTF_8));
        assets.upsertProducedOutput(outputAssetId, filename, output.getSize(), output.getContentType(),
                storageUrl, Asset.UploadStatus.COMPLETED.name(), Instant.now(), job.getProjectId(),
                job.getId(), input.getId(), input.getExternalSubjectId(), input.getExternalReference());

        return assets.findByProducingJobIdAndProjectId(job.getId(), job.getProjectId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Durable run output disappeared after upsert"));
    }

    private String extension(String contentType) {
        if ("image/webp".equals(contentType)) return ".webp";
        if ("image/png".equals(contentType)) return ".png";
        if ("image/jpeg".equals(contentType)) return ".jpg";
        return ".bin";
    }
}

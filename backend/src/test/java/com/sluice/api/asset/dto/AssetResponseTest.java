package com.sluice.api.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.domain.Asset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesLineageWithoutExposingPrivateStorageUrl() throws Exception {
        UUID projectId = UUID.randomUUID();
        Asset parent = new Asset(UUID.randomUUID(), "source.png", 12, "image/png", "https://private/source",
                Asset.UploadStatus.COMPLETED, Instant.parse("2026-08-28T10:00:00Z"), projectId);
        Asset derived = new Asset(UUID.randomUUID(), "source.webp", 8, "image/webp", "https://private/derived",
                Asset.UploadStatus.COMPLETED, Instant.parse("2026-08-28T10:01:00Z"), projectId);
        UUID producingJobId = UUID.randomUUID();
        derived.setParentAsset(parent);
        derived.setProducingJobId(producingJobId);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(AssetResponse.from(derived)));

        assertFalse(json.has("storageUrl"));
        assertEquals(parent.getId().toString(), json.path("parentAssetId").asText());
        assertEquals(producingJobId.toString(), json.path("producingJobId").asText());
        assertTrue(json.path("filename").asText().contains("source.webp"));
    }

    @Test
    void legacyUploadResponseKeepsShapeWithoutPrivateStorageUrl() throws Exception {
        UploadAssetResponse response = new UploadAssetResponse(UUID.randomUUID(), "source.png", 12,
                "image/png", "https://private/source", Instant.now(), UUID.randomUUID(), "QUEUED", Instant.now());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertFalse(json.has("storageUrl"));
        assertTrue(json.has("assetId"));
        assertTrue(json.has("jobStatus"));
    }
}

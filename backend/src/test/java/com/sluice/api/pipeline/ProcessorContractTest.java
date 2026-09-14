package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorContractTest {
    @Test
    void mediaContractMatchesExactAndWildcardTypes() {
        MediaContract contract = new MediaContract("image", List.of("image/png", "image/*"), 10_000, 1_000, true, false);

        assertTrue(contract.accepts("image/png"));
        assertTrue(contract.accepts("image/jpeg"));
        assertFalse(contract.accepts("video/mp4"));
    }

    @Test
    void manifestHasStableVersionedIdentity() {
        ProcessorManifest manifest = new ProcessorManifest(
                "1", "image.resize", "1.0.0", "Resize", "Resize images", "transform",
                MediaContract.any("image", List.of("image/*")),
                MediaContract.any("image", List.of("image/jpeg")),
                JsonNodeFactory.instance.objectNode(),
                new ProcessorLimits(30, 512, 50_000_000),
                List.of("blob.read.input", "blob.write.output"), "PUBLISHED", "Initial release");

        assertEquals("image.resize@1.0.0", manifest.key());
        assertEquals("PUBLISHED", manifest.status());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessorLimits(0, 512, 10));
        assertThrows(IllegalArgumentException.class, () -> new MediaContract("image", List.of(), 0, 0, false, false));
    }

    @Test
    void builtInManifestFixturesAreImmutableVersionedContracts() {
        List<String> resources = List.of(
                "mime-validation-1.0.0.json",
                "metadata-1.0.0.json",
                "checksum-1.0.0.json",
                "resize-1.0.0.json",
                "resize-2.0.0.json",
                "strip-metadata-1.0.0.json",
                "webp-1.0.0.json",
                "webp-2.0.0.json",
                "governance-content-safety-1.0.0.json");
        Set<String> expectedKeys = Set.of(
                "mime-validation@1.0.0",
                "metadata@1.0.0",
                "checksum@1.0.0",
                "resize@1.0.0",
                "resize@2.0.0",
                "strip-metadata@1.0.0",
                "webp@1.0.0",
                "webp@2.0.0",
                "governance.content-safety@1.0.0");

        Set<String> actualKeys = resources.stream().map(resource -> {
            ProcessorManifest manifest = ProcessorManifestResources.load(resource);
            assertEquals("PUBLISHED", manifest.status());
            assertEquals("object", manifest.configSchema().path("type").asText());
            assertFalse(manifest.configSchema().path("additionalProperties").asBoolean(true));
            return manifest.key();
        }).collect(java.util.stream.Collectors.toSet());

        assertEquals(expectedKeys, actualKeys);
    }

    @Test
    void nonSemanticVersionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessorManifest(
                "1", "image.resize", "latest", "Resize", "Resize images", "transform",
                MediaContract.any("image", List.of("image/*")),
                MediaContract.any("image", List.of("image/jpeg")),
                JsonNodeFactory.instance.objectNode(), new ProcessorLimits(30, 512, 50_000_000),
                List.of(), "PUBLISHED", ""));
    }
}

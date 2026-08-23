package com.sluice.api.pipeline.controller;

import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.catalog.ProcessorCatalogService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessorControllerTest {
    @Test
    void exactReleaseIncludesThePersistedSchemaAndPublisher() {
        ProcessorCatalogService catalog = mock(ProcessorCatalogService.class);
        ProcessorManifest manifest = ProcessorManifestResources.load("resize-1.0.0.json");
        when(catalog.getPublished("resize", "1.0.0")).thenReturn(
                new ProcessorCatalogService.CatalogRelease(manifest, "Sluice", "PUBLIC", Instant.EPOCH));

        var response = new ProcessorController(catalog).getVersion("resize", "1.0.0");

        assertEquals("resize", response.slug());
        assertEquals("Sluice", response.publisher());
        assertFalse(response.configSchema().path("additionalProperties").asBoolean(true));
    }

    @Test
    void listEndpointKeepsReturningAnArrayOfReleases() {
        ProcessorCatalogService catalog = mock(ProcessorCatalogService.class);
        ProcessorManifest manifest = ProcessorManifestResources.load("checksum-1.0.0.json");
        when(catalog.listPublished()).thenReturn(List.of(
                new ProcessorCatalogService.CatalogRelease(manifest, "Sluice", "PUBLIC", Instant.EPOCH)));

        assertEquals(1, new ProcessorController(catalog).getProcessors().size());
    }
}

package com.sluice.api.pipeline.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.catalog.ProcessorCatalogService;
import com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement;
import com.sluice.api.pipeline.catalog.domain.ProcessorDefinition;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.service.ProcessorEnablementService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ProjectProcessorControllerTest {
    @Test
    void projectCatalogMarksOnlyThisProjectsEnabledRelease() {
        ProcessorEnablementService enablements = mock(ProcessorEnablementService.class);
        ProcessorCatalogService catalog = mock(ProcessorCatalogService.class);
        ProcessorManifest manifest = ProcessorManifestResources.load("resize-1.0.0.json");
        UUID projectId = UUID.randomUUID();
        ProjectProcessorReleaseEnablement enabled = mock(ProjectProcessorReleaseEnablement.class);
        ProcessorVersion version = mock(ProcessorVersion.class);
        ProcessorDefinition definition = mock(ProcessorDefinition.class);
        when(enabled.getProcessorVersion()).thenReturn(version);
        when(version.getDefinition()).thenReturn(definition);
        when(definition.getSlug()).thenReturn("resize");
        when(version.getSemanticVersion()).thenReturn("1.0.0");
        when(enabled.getEnabledAt()).thenReturn(Instant.EPOCH);
        when(enabled.getUpdatedAt()).thenReturn(Instant.EPOCH);
        when(enablements.list(eq(projectId), any(ProjectContext.class)))
                .thenReturn(List.of(enabled));
        when(catalog.listMarketReleases()).thenReturn(List.of(
                new ProcessorCatalogService.CatalogRelease(manifest, "PUBLISHED", "Sluice", "PUBLIC", Instant.EPOCH)));

        var response = new ProjectProcessorController(enablements, catalog).list(projectId,
                new ProjectContext(projectId, UUID.randomUUID(), false));

        assertTrue(response.get(0).enabled());
    }
}

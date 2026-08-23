package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.MediaContract;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineAlias;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineAliasRepository;
import com.sluice.api.pipeline.repository.PipelineRepository;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void firstPublishFreezesDraftAndCreatesStableAlias() throws Exception {
        Fixture fixture = fixture("DRAFT");
        MediaContract contract = MediaContract.any("image", List.of("image/jpeg"));
        when(fixture.validator.validateDefinition("product-images", fixture.version.getDefinition()))
                .thenReturn(new PipelineValidationReport(true, List.of(), contract, contract));
        when(fixture.aliases.findByPipelineIdAndAlias(fixture.pipeline.getId(), "stable")).thenReturn(Optional.empty());

        var published = fixture.service.publish("product-images", 1, fixture.context);

        assertEquals("PUBLISHED", published.status());
        assertThrows(IllegalStateException.class, () -> fixture.version.updateDraft(
                mapper.createObjectNode(), "image/png", null, null, null));
        verify(fixture.aliases).save(any(PipelineAlias.class));
    }

    @Test
    void aliasCannotTargetMutableDraft() throws Exception {
        Fixture fixture = fixture("DRAFT");
        when(fixture.versions.findByPipelineIdAndVersionNumber(fixture.pipeline.getId(), 1))
                .thenReturn(Optional.of(fixture.version));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.moveAlias("product-images", "stable", 1, fixture.context));
    }

    private Fixture fixture(String status) throws Exception {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
        PipelineAliasRepository aliases = mock(PipelineAliasRepository.class);
        PipelineValidator validator = mock(PipelineValidator.class);
        UUID projectId = UUID.randomUUID();
        Pipeline pipeline = new Pipeline(UUID.randomUUID(), "product-images", "Product images", null, projectId);
        PipelineVersion version = new PipelineVersion(UUID.randomUUID(), pipeline, 1, status, "image/jpeg",
                mapper.readTree("""
                        {"schemaVersion":"1","slug":"product-images","input":{"kind":"image","mimeTypes":["image/jpeg"]},
                         "steps":[{"id":"resize","processor":"resize","version":"1.0.0","config":{}}]}
                        """));
        ProjectContext context = new ProjectContext(projectId, UUID.randomUUID(), false);
        when(pipelines.findBySlugAndProjectId("product-images", projectId)).thenReturn(Optional.of(pipeline));
        when(pipelines.findByIdAndProjectIdForUpdate(pipeline.getId(), projectId)).thenReturn(Optional.of(pipeline));
        when(versions.findFirstByPipelineIdAndStatus(pipeline.getId(), "DRAFT")).thenReturn(Optional.of(version));
        when(versions.save(version)).thenReturn(version);
        return new Fixture(new PipelineService(pipelines, versions, aliases, validator, mapper), pipelines, versions,
                aliases, validator, pipeline, version, context);
    }

    private record Fixture(PipelineService service, PipelineRepository pipelines, PipelineVersionRepository versions,
                           PipelineAliasRepository aliases, PipelineValidator validator, Pipeline pipeline,
                           PipelineVersion version, ProjectContext context) {}
}

package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcessorRegistry registry = mock(ProcessorRegistry.class);
    private final ProcessorVersionRepository versions = mock(ProcessorVersionRepository.class);
    private final Processor processor = mock(Processor.class);

    @Test
    void publishedPipelineStillResolvesWithoutProjectEnablementState() throws Exception {
        registerPersistedStatus("PUBLISHED");

        Pipeline pipeline = new PipelineResolver(registry, versions).resolve(definition());

        assertEquals(1, pipeline.getSteps().size());
    }

    @Test
    void persistedGlobalDisableBlocksRuntimeResolution() throws Exception {
        registerPersistedStatus("DISABLED");

        assertThrows(IllegalStateException.class,
                () -> new PipelineResolver(registry, versions).resolve(definition()));
    }

    private void registerPersistedStatus(String status) {
        ProcessorManifest manifest = ProcessorManifestResources.load("resize-1.0.0.json");
        ProcessorVersion persisted = mock(ProcessorVersion.class);
        when(processor.getManifest()).thenReturn(manifest);
        when(registry.get("resize", "1.0.0")).thenReturn(processor);
        when(persisted.getLifecycleStatus()).thenReturn(status);
        when(versions.findByDefinitionSlugAndSemanticVersion("resize", "1.0.0"))
                .thenReturn(Optional.of(persisted));
    }

    private com.fasterxml.jackson.databind.JsonNode definition() throws Exception {
        return mapper.readTree("""
                {"steps":[{"id":"resize","processor":"resize","version":"1.0.0","config":{}}]}
                """);
    }
}

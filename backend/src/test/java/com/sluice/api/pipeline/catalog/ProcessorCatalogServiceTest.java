package com.sluice.api.pipeline.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.catalog.domain.ProcessorDefinition;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.repository.ProcessorDefinitionRepository;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessorCatalogServiceTest {
    private final ProcessorDefinitionRepository definitions = mock(ProcessorDefinitionRepository.class);
    private final ProcessorVersionRepository versions = mock(ProcessorVersionRepository.class);
    private final ProcessorRegistry registry = mock(ProcessorRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessorCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ProcessorCatalogService(definitions, versions, registry, objectMapper);
    }

    @Test
    void insertsAReviewedManifestWhenTheReleaseIsNew() {
        ProcessorManifest manifest = ProcessorManifestResources.load("checksum-1.0.0.json");
        ProcessorDefinition definition = new ProcessorDefinition(UUID.randomUUID(), manifest, "Sluice", "PUBLIC");
        when(registry.getAllManifests()).thenReturn(List.of(manifest));
        when(definitions.findBySlug("checksum")).thenReturn(Optional.empty());
        when(definitions.save(any())).thenReturn(definition);
        when(versions.findByDefinitionSlugAndSemanticVersion("checksum", "1.0.0"))
                .thenReturn(Optional.empty());
        when(versions.findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED"))
                .thenReturn(List.of());

        service.synchronizeAndAudit();

        verify(definitions).save(any(ProcessorDefinition.class));
        verify(versions).save(any(ProcessorVersion.class));
        verify(versions).flush();
    }

    @Test
    void rejectsChangedContentForAnExistingPublishedRelease() {
        ProcessorManifest manifest = ProcessorManifestResources.load("checksum-1.0.0.json");
        ProcessorDefinition definition = new ProcessorDefinition(UUID.randomUUID(), manifest, "Sluice", "PUBLIC");
        var changed = objectMapper.valueToTree(manifest);
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("description", "changed after publication");
        ProcessorVersion persisted = new ProcessorVersion(UUID.randomUUID(), definition, "1.0.0", "PUBLISHED",
                manifest.key(), "1", changed, Instant.now());
        when(registry.getAllManifests()).thenReturn(List.of(manifest));
        when(definitions.findBySlug("checksum")).thenReturn(Optional.of(definition));
        when(versions.findByDefinitionSlugAndSemanticVersion("checksum", "1.0.0"))
                .thenReturn(Optional.of(persisted));

        assertThrows(ProcessorCatalogMismatchException.class, service::synchronizeAndAudit);
    }

    @Test
    void acceptsAnUnchangedManifestAfterJsonbNormalizesNumericNodeTypes() throws Exception {
        ProcessorManifest manifest = ProcessorManifestResources.load("checksum-1.0.0.json");
        ProcessorDefinition definition = new ProcessorDefinition(UUID.randomUUID(), manifest, "Sluice", "PUBLIC");
        var implementationTree = objectMapper.valueToTree(manifest);
        var jsonbRoundTrip = objectMapper.readTree(objectMapper.writeValueAsBytes(implementationTree));
        assertNotEquals(implementationTree, jsonbRoundTrip);
        ProcessorVersion persisted = new ProcessorVersion(UUID.randomUUID(), definition, "1.0.0", "PUBLISHED",
                manifest.key(), "1", jsonbRoundTrip, Instant.now());
        when(registry.getAllManifests()).thenReturn(List.of(manifest));
        when(definitions.findBySlug("checksum")).thenReturn(Optional.of(definition));
        when(versions.findByDefinitionSlugAndSemanticVersion("checksum", "1.0.0"))
                .thenReturn(Optional.of(persisted));
        when(versions.findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED"))
                .thenReturn(List.of());

        assertDoesNotThrow(service::synchronizeAndAudit);
    }

    @Test
    void rejectsPublishedRowsWithoutARegisteredImplementation() {
        ProcessorManifest manifest = ProcessorManifestResources.load("checksum-1.0.0.json");
        ProcessorDefinition definition = new ProcessorDefinition(UUID.randomUUID(), manifest, "Sluice", "PUBLIC");
        ProcessorVersion persisted = new ProcessorVersion(UUID.randomUUID(), definition, "1.0.0", "PUBLISHED",
                manifest.key(), "1", objectMapper.valueToTree(manifest), Instant.now());
        when(registry.getAllManifests()).thenReturn(List.of());
        when(versions.findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED"))
                .thenReturn(List.of(persisted));
        when(registry.find("checksum", "1.0.0")).thenReturn(Optional.empty());

        assertThrows(ProcessorCatalogMismatchException.class, service::synchronizeAndAudit);
    }
}

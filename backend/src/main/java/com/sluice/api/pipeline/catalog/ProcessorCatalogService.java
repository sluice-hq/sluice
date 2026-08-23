package com.sluice.api.pipeline.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.catalog.domain.ProcessorDefinition;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.repository.ProcessorDefinitionRepository;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessorCatalogService {
    private static final String FIRST_PARTY_PUBLISHER = "Sluice";
    private static final String PUBLIC_VISIBILITY = "PUBLIC";

    private final ProcessorDefinitionRepository definitionRepository;
    private final ProcessorVersionRepository versionRepository;
    private final ProcessorRegistry registry;
    private final ObjectMapper objectMapper;

    public ProcessorCatalogService(ProcessorDefinitionRepository definitionRepository,
                                   ProcessorVersionRepository versionRepository,
                                   ProcessorRegistry registry,
                                   ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void synchronizeAndAudit() {
        for (ProcessorManifest manifest : registry.getAllManifests()) {
            ProcessorDefinition definition = definitionRepository.findBySlug(manifest.slug())
                    .orElseGet(() -> definitionRepository.save(new ProcessorDefinition(
                            UUID.randomUUID(), manifest, FIRST_PARTY_PUBLISHER, PUBLIC_VISIBILITY)));
            var persisted = versionRepository.findByDefinitionSlugAndSemanticVersion(
                    manifest.slug(), manifest.version());
            if (persisted.isPresent()) {
                assertMatches(persisted.get(), manifest);
            } else {
                versionRepository.save(new ProcessorVersion(
                        UUID.randomUUID(), definition, manifest.version(), manifest.status(), manifest.key(),
                        manifest.schemaVersion(), objectMapper.valueToTree(manifest), Instant.now()));
            }
        }
        versionRepository.flush();

        for (ProcessorVersion persisted : versionRepository
                .findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED")) {
            ProcessorManifest implementation = registry.find(
                    persisted.getDefinition().getSlug(), persisted.getSemanticVersion())
                    .orElseThrow(() -> new ProcessorCatalogMismatchException(
                            "Published processor has no registered implementation: " + persisted.getImplementationKey()));
            assertMatches(persisted, implementation);
        }
    }

    @Transactional(readOnly = true)
    public List<CatalogRelease> listPublished() {
        return versionRepository.findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED")
                .stream().map(this::toRelease).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogRelease> listPublished(String slug) {
        return versionRepository.findByDefinitionSlugAndLifecycleStatusOrderBySemanticVersionDesc(slug, "PUBLISHED")
                .stream().map(this::toRelease).toList();
    }

    @Transactional(readOnly = true)
    public CatalogRelease getPublished(String slug, String version) {
        ProcessorVersion persisted = versionRepository.findByDefinitionSlugAndSemanticVersion(slug, version)
                .filter(release -> "PUBLISHED".equals(release.getLifecycleStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown processor release"));
        return toRelease(persisted);
    }

    private CatalogRelease toRelease(ProcessorVersion persisted) {
        try {
            ProcessorManifest manifest = objectMapper.treeToValue(persisted.getManifest(), ProcessorManifest.class);
            return new CatalogRelease(manifest, persisted.getDefinition().getPublisher(),
                    persisted.getDefinition().getVisibility(), persisted.getPublishedAt());
        } catch (JsonProcessingException exception) {
            throw new ProcessorCatalogMismatchException(
                    "Persisted processor manifest cannot be read: " + persisted.getImplementationKey());
        }
    }

    private void assertMatches(ProcessorVersion persisted, ProcessorManifest implementation) {
        try {
            ProcessorManifest persistedManifest = objectMapper.treeToValue(persisted.getManifest(), ProcessorManifest.class);
            if (!persisted.getImplementationKey().equals(implementation.key())
                    || !persistedManifest.equals(implementation)) {
                throw new ProcessorCatalogMismatchException(
                        "Published processor manifest differs from its implementation: " + implementation.key());
            }
        } catch (JsonProcessingException exception) {
            throw new ProcessorCatalogMismatchException(
                    "Published processor manifest cannot be read: " + persisted.getImplementationKey());
        }
    }

    public record CatalogRelease(ProcessorManifest manifest, String publisher, String visibility,
                                 Instant publishedAt) {
    }
}

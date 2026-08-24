package com.sluice.api.pipeline.catalog;

import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.catalog.repository.ProcessorDefinitionRepository;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SluiceIntegrationTest
class ProcessorCatalogPersistenceTests {
    @Autowired
    private ProcessorDefinitionRepository definitionRepository;

    @Autowired
    private ProcessorVersionRepository versionRepository;

    @Autowired
    private ProcessorRegistry processorRegistry;

    @Test
    void flywayCatalogContainsEveryRegisteredPublishedManifest() {
        var manifests = processorRegistry.getAllManifests();
        var registeredDefinitionCount = manifests.stream()
                .map(manifest -> manifest.slug())
                .distinct()
                .count();
        assertEquals(registeredDefinitionCount, definitionRepository.count());
        var releases = versionRepository
                .findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED");
        assertEquals(manifests.size(), releases.size());
        assertFalse(releases.stream().anyMatch(release ->
                !release.getManifest().path("configSchema").path("type").asText().equals("object")));
    }
}

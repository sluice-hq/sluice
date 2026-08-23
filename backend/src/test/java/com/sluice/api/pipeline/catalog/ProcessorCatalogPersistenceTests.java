package com.sluice.api.pipeline.catalog;

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

    @Test
    void flywayCatalogContainsEveryRegisteredPublishedManifest() {
        assertEquals(5, definitionRepository.count());
        var releases = versionRepository
                .findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc("PUBLISHED");
        assertEquals(5, releases.size());
        assertFalse(releases.stream().anyMatch(release ->
                !release.getManifest().path("configSchema").path("type").asText().equals("object")));
    }
}

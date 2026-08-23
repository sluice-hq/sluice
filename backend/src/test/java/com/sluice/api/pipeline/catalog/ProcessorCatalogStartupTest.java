package com.sluice.api.pipeline.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProcessorCatalogStartupTest {
    @Test
    void synchronizesAndAuditsOnApplicationStartup() {
        ProcessorCatalogService service = mock(ProcessorCatalogService.class);

        new ProcessorCatalogStartup(service).run(new DefaultApplicationArguments());

        verify(service).synchronizeAndAudit();
    }
}

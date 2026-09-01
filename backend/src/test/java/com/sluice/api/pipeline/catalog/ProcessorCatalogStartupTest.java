package com.sluice.api.pipeline.catalog;

import com.sluice.api.runtime.SluiceRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessorCatalogStartupTest {
    @Test
    void apiRuntimeOwnsCatalogSynchronization() {
        ProcessorCatalogService service = mock(ProcessorCatalogService.class);
        SluiceRuntimeProperties properties = new SluiceRuntimeProperties();
        properties.setMode(SluiceRuntimeProperties.Mode.API);

        new ProcessorCatalogStartup(service, properties).run(new DefaultApplicationArguments());

        verify(service).synchronizeAndAudit();
        verify(service, never()).auditPublishedImplementations();
    }

    @Test
    void workerRuntimePerformsReadOnlyCatalogAudit() {
        ProcessorCatalogService service = mock(ProcessorCatalogService.class);
        SluiceRuntimeProperties properties = new SluiceRuntimeProperties();
        properties.setMode(SluiceRuntimeProperties.Mode.WORKER);

        new ProcessorCatalogStartup(service, properties).run(new DefaultApplicationArguments());

        verify(service).auditPublishedImplementations();
        verify(service, never()).synchronizeAndAudit();
    }
}

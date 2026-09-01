package com.sluice.api.pipeline.catalog;

import com.sluice.api.runtime.SluiceRuntimeProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Synchronizes from API/all runtimes and performs a read-only published-release audit in worker-only mode. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProcessorCatalogStartup implements ApplicationRunner {
    private final ProcessorCatalogService catalogService;
    private final SluiceRuntimeProperties runtimeProperties;

    public ProcessorCatalogStartup(ProcessorCatalogService catalogService,
                                   SluiceRuntimeProperties runtimeProperties) {
        this.catalogService = catalogService;
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (runtimeProperties.getMode() == SluiceRuntimeProperties.Mode.WORKER) {
            catalogService.auditPublishedImplementations();
        } else {
            catalogService.synchronizeAndAudit();
        }
    }
}

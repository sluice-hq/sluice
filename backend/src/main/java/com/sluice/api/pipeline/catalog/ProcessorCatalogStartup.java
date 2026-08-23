package com.sluice.api.pipeline.catalog;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Synchronizes reviewed manifests only after Flyway/JPA startup and fails startup on catalog drift. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProcessorCatalogStartup implements ApplicationRunner {
    private final ProcessorCatalogService catalogService;

    public ProcessorCatalogStartup(ProcessorCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        catalogService.synchronizeAndAudit();
    }
}

package com.sluice.api.webhook.service;

import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.webhook.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnApiRuntime
public class WebhookSecretMigrationStartup implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(WebhookSecretMigrationStartup.class);

    private final WebhookSecretMigrationBatch migration;
    private final WebhookEndpointRepository endpoints;
    private final SluiceMetrics metrics;

    public WebhookSecretMigrationStartup(WebhookSecretMigrationBatch migration, WebhookEndpointRepository endpoints,
                                         SluiceMetrics metrics) {
        this.migration = migration;
        this.endpoints = endpoints;
        this.metrics = metrics;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int migrated = 0;
        try {
            int batch;
            do {
                batch = migration.migrateNextBatch();
                migrated += batch;
            } while (batch > 0);
            if (!migration.retainsLegacyPlaintext()) {
                do {
                    batch = migration.purgeNextLegacyBatch();
                } while (batch > 0);
            }
            long remaining = endpoints.countByEncryptionVersionIsNull();
            if (remaining > 0) {
                throw new IllegalStateException("Legacy webhook signing secrets remain after migration");
            }
            if (!migration.retainsLegacyPlaintext() && endpoints.countWithLegacySecret() > 0) {
                throw new IllegalStateException("Plaintext webhook signing secrets remain after migration");
            }
            if (migrated > 0) {
                log.info("Protected {} legacy webhook signing secret(s)", migrated);
            }
        } catch (RuntimeException exception) {
            metrics.webhookSecretMigration("failed", 1);
            log.error("Webhook signing-secret migration failed; API startup is blocked");
            throw new IllegalStateException("Webhook signing-secret migration failed", exception);
        }
    }
}

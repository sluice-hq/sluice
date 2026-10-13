package com.sluice.api.webhook.service;

import java.util.Locale;

public enum WebhookSecretMigrationMode {
    DUAL_WRITE,
    PROTECTED;

    public static WebhookSecretMigrationMode parse(String value) {
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Webhook secret migration mode must be dual-write or protected", exception);
        }
    }

    public boolean retainsLegacyPlaintext() {
        return this == DUAL_WRITE;
    }
}

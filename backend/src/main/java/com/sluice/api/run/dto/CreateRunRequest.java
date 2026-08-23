package com.sluice.api.run.dto;

import java.util.UUID;

public record CreateRunRequest(String pipeline, String alias, Integer version, UUID inputAssetId, Callback callback) {
    public CreateRunRequest(String pipeline, String alias, Integer version, UUID inputAssetId) {
        this(pipeline, alias, version, inputAssetId, null);
    }
    public record Callback(UUID webhookEndpointId) {}
}

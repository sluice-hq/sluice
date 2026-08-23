package com.sluice.api.run.dto;

import java.util.UUID;

public record CreateRunRequest(String pipeline, String alias, Integer version, UUID inputAssetId) {
}

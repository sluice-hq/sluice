package com.sluice.api.run.dto;

import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.step.domain.StepRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunResponse(UUID id, String status, PipelineReference pipeline, UUID inputAssetId,
                          List<StepResponse> steps, List<AssetResponse> outputs,
                          Instant createdAt, Instant updatedAt) {
    public record PipelineReference(String slug, int version) {}

    public record StepResponse(UUID id, String stepId, String processor, String version, String status,
                               Instant createdAt, Instant updatedAt) {
        public static StepResponse from(StepRun step) {
            return new StepResponse(step.getId(), step.getStepId(), step.getProcessorSlug(),
                    step.getProcessorVersion(), step.getStatus(), step.getCreatedAt(), step.getUpdatedAt());
        }
    }
}

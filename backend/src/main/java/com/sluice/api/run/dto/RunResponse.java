package com.sluice.api.run.dto;

import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.step.domain.StepRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunResponse(UUID id, String status, PipelineReference pipeline, UUID inputAssetId,
                          List<StepResponse> steps, List<AssetResponse> outputs,
                          Instant createdAt, Instant updatedAt, Metrics metrics, ErrorResponse error,
                          List<AttemptResponse> attempts) {
    public RunResponse(UUID id, String status, PipelineReference pipeline, UUID inputAssetId,
                       List<StepResponse> steps, List<AssetResponse> outputs,
                       Instant createdAt, Instant updatedAt) {
        this(id, status, pipeline, inputAssetId, steps, outputs, createdAt, updatedAt,
                new Metrics(null, null, null, null, null, null), null, List.of());
    }
    public record PipelineReference(String slug, int version) {}

    public record StepResponse(UUID id, String stepId, String processor, String version, String status,
                               Instant createdAt, Instant updatedAt, int attempt, Instant startedAt,
                               Instant completedAt, Long durationMs, Long inputBytes, Long outputBytes,
                               String inputMimeType, String outputMimeType, com.fasterxml.jackson.databind.JsonNode metadata,
                               UUID outputAssetId, ErrorResponse error) {
        public static StepResponse from(StepRun step) {
            return new StepResponse(step.getId(), step.getStepId(), step.getProcessorSlug(),
                    step.getProcessorVersion(), step.getStatus(), step.getCreatedAt(), step.getUpdatedAt(),
                    step.getAttemptNumber(), step.getStartedAt(), step.getCompletedAt(), step.getDurationMs(),
                    step.getInputBytes(), step.getOutputBytes(), step.getInputMimeType(), step.getOutputMimeType(),
                    step.getMetadata(), step.getOutputAssetId(), step.getErrorCode() == null ? null
                    : new ErrorResponse(step.getErrorCode(), step.getErrorMessage()));
        }
    }

    public record Metrics(Long queueWaitMs, Long processingMs, Long inputBytes, Long outputBytes,
                          Long bytesSaved, java.math.BigDecimal compressionRatio) {}
    public record ErrorResponse(String code, String message) {}
    public record AttemptResponse(int attempt, String status, Instant startedAt, Instant completedAt,
                                  ErrorResponse error, Boolean transientFailure) {}
}

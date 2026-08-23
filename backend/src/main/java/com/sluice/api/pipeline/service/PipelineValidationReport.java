package com.sluice.api.pipeline.service;

import com.sluice.api.pipeline.MediaContract;

import java.util.List;

public record PipelineValidationReport(
        boolean valid,
        List<PipelineValidationError> errors,
        MediaContract inputContract,
        MediaContract outputContract
) {
    public PipelineValidationReport {
        errors = List.copyOf(errors);
    }
}

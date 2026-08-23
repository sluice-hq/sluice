package com.sluice.api.pipeline.service;

public class PipelineValidationException extends IllegalArgumentException {
    private final PipelineValidationReport report;

    public PipelineValidationException(PipelineValidationReport report) {
        super("Pipeline validation failed");
        this.report = report;
    }

    public PipelineValidationReport getReport() { return report; }
}

package com.sluice.api.pipeline.service;

public record PipelineValidationError(String path, String code, String message) {}

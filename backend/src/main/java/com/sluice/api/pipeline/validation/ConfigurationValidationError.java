package com.sluice.api.pipeline.validation;

public record ConfigurationValidationError(String path, String code, String message) {
}

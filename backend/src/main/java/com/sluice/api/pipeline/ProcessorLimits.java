package com.sluice.api.pipeline;

public record ProcessorLimits(
        int timeoutSeconds,
        int memoryMb,
        long maxOutputBytes
) {
    public ProcessorLimits {
        if (timeoutSeconds <= 0 || memoryMb <= 0 || maxOutputBytes <= 0) {
            throw new IllegalArgumentException("Processor limits must be positive");
        }
    }
}

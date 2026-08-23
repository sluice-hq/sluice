package com.sluice.api.pipeline.validation;

import java.util.List;

public class ProcessorConfigurationException extends IllegalArgumentException {
    private final String processor;
    private final List<ConfigurationValidationError> errors;

    public ProcessorConfigurationException(String processor, List<ConfigurationValidationError> errors) {
        super("Processor configuration is invalid: " + processor);
        this.processor = processor;
        this.errors = List.copyOf(errors);
    }

    public String getProcessor() {
        return processor;
    }

    public List<ConfigurationValidationError> getErrors() {
        return errors;
    }
}

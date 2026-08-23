package com.sluice.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import com.sluice.api.pipeline.validation.ConfigurationValidationError;
import com.sluice.api.pipeline.validation.ProcessorConfigurationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    @Test
    void doesNotExposeIllegalArgumentDetails() {
        ProblemDetail response = new GlobalExceptionHandler()
                .handleIllegalArgument(new IllegalArgumentException("internal storage URL"));

        assertEquals(400, response.getStatus());
        assertEquals("The request is invalid.", response.getDetail());
        assertEquals("invalid_request", response.getProperties().get("code"));
        assertFalse(response.getDetail().contains("storage URL"));
    }

    @Test
    void mapsDatabaseConflictsWithoutExposingConstraintDetails() {
        ProblemDetail response = new GlobalExceptionHandler()
                .handleDataIntegrityViolation(new DataIntegrityViolationException("users_email_key"));

        assertEquals(409, response.getStatus());
        assertEquals("resource_conflict", response.getProperties().get("code"));
        assertFalse(response.getDetail().contains("users_email_key"));
    }

    @Test
    void exposesStructuredProcessorConfigurationErrors() {
        ProblemDetail response = new GlobalExceptionHandler().handleProcessorConfiguration(
                new ProcessorConfigurationException("resize@1.0.0", List.of(
                        new ConfigurationValidationError("/steps/0/config/width", "type",
                                "Configuration value has the wrong type."))));

        assertEquals(400, response.getStatus());
        assertEquals("processor_configuration_invalid", response.getProperties().get("code"));
        assertEquals("resize@1.0.0", response.getProperties().get("processor"));
        assertEquals(1, ((List<?>) response.getProperties().get("errors")).size());
    }
}

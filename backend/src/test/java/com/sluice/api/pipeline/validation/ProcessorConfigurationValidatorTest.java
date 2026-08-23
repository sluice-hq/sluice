package com.sluice.api.pipeline.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorManifestResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessorConfigurationValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessorConfigurationValidator validator = new ProcessorConfigurationValidator();
    private final ProcessorManifest resize = ProcessorManifestResources.load("resize-1.0.0.json");

    @Test
    void acceptsConfigurationThatMatchesTheManifestSchema() throws Exception {
        assertDoesNotThrow(() -> validator.validate(resize,
                objectMapper.readTree("{\"width\":800,\"height\":600}")));
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        ProcessorConfigurationException exception = assertThrows(ProcessorConfigurationException.class,
                () -> validator.validate(resize, objectMapper.readTree("{\"widht\":800}")));

        assertEquals("resize@1.0.0", exception.getProcessor());
        assertEquals("additionalProperties", exception.getErrors().get(0).code());
    }

    @Test
    void rejectsWrongTypesAtAStablePath() throws Exception {
        ProcessorConfigurationException exception = assertThrows(ProcessorConfigurationException.class,
                () -> validator.validate(resize, objectMapper.readTree("{\"width\":\"800\"}")));

        assertEquals("type", exception.getErrors().get(0).code());
        assertEquals("/width", exception.getErrors().get(0).path());
    }
}

package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.domain.PipelineVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class PipelineValidatorTest {

    private ProcessorRegistry registry;
    private PipelineValidator validator;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        registry = Mockito.mock(ProcessorRegistry.class);
        validator = new PipelineValidator(registry);
    }

    @Test
    void testValidPipeline() throws Exception {
        String json = """
        {
            "steps": [
                { "processor": "resize", "config": { "width": 800 } }
            ]
        }
        """;
        
        PipelineVersion version = new PipelineVersion(
                UUID.randomUUID(), null, 1, "DRAFT", "image/jpeg", mapper.readTree(json));

        ProcessorMetadata meta = new ProcessorMetadata("resize", List.of("image/*"), (in, cfg) -> in);
        com.sluice.api.pipeline.Processor processor = Mockito.mock(com.sluice.api.pipeline.Processor.class);
        when(processor.getMetadata()).thenReturn(meta);
        when(registry.get("resize")).thenReturn(processor);

        assertDoesNotThrow(() -> validator.validate(version));
    }

    @Test
    void testInvalidMimeType() throws Exception {
        String json = """
        {
            "steps": [
                { "processor": "resize", "config": { "width": 800 } }
            ]
        }
        """;
        
        PipelineVersion version = new PipelineVersion(
                UUID.randomUUID(), null, 1, "DRAFT", "application/pdf", mapper.readTree(json));

        ProcessorMetadata meta = new ProcessorMetadata("resize", List.of("image/*"), (in, cfg) -> in);
        com.sluice.api.pipeline.Processor processor = Mockito.mock(com.sluice.api.pipeline.Processor.class);
        when(processor.getMetadata()).thenReturn(meta);
        when(registry.get("resize")).thenReturn(processor);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(version));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("does not accept MIME type 'application/pdf'"));
    }
}

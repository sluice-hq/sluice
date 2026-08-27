package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.validation.ProcessorConfigurationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ProcessorRegistry registry;
    private PipelineValidator validator;

    @BeforeEach
    void setup() {
        registry = mock(ProcessorRegistry.class);
        validator = new PipelineValidator(registry, new ProcessorConfigurationValidator());
    }

    @Test
    void acceptsCanonicalDefinitionWithExactProcessorVersion() throws Exception {
        register("resize", "resize-1.0.0.json");
        var report = validator.validateDefinition("product-images", mapper.readTree(definition(
                "{\"id\":\"resize\",\"processor\":\"resize\",\"version\":\"1.0.0\",\"config\":{\"width\":800}}")));

        assertTrue(report.valid());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void acceptsDeprecatedProcessorReleaseWhenItRemainsRegistered() throws Exception {
        var base = ProcessorManifestResources.load("resize-1.0.0.json");
        var deprecated = new com.sluice.api.pipeline.ProcessorManifest(
                base.schemaVersion(), base.slug(), base.version(), base.displayName(), base.description(),
                base.category(), base.input(), base.output(), base.configSchema(), base.limits(),
                base.permissions(), "DEPRECATED", "Use resize v2 for new pipelines.");
        Processor processor = mock(Processor.class);
        when(processor.getManifest()).thenReturn(deprecated);
        when(processor.getMetadata()).thenReturn(new ProcessorMetadata("resize", deprecated.input().mimeTypes(),
                (input, config) -> input, deprecated));
        when(registry.get("resize", "1.0.0")).thenReturn(processor);

        var report = validator.validateDefinition("product-images", mapper.readTree(definition(
                "{\"id\":\"resize\",\"processor\":\"resize\",\"version\":\"1.0.0\",\"config\":{\"width\":800}}")));

        assertTrue(report.valid());
    }

    @Test
    void rejectsFloatingProcessorVersionAndDuplicateStepIds() throws Exception {
        var report = validator.validateDefinition("product-images", mapper.readTree(definition(
                "{\"id\":\"resize\",\"processor\":\"resize\",\"config\":{}}," +
                "{\"id\":\"resize\",\"processor\":\"resize\",\"config\":{}}")));

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.code().equals("processor_version_required")));
        assertTrue(report.errors().stream().anyMatch(error -> error.code().equals("step_id_duplicate")));
    }

    @Test
    void reportsSchemaAndMediaCompatibilityErrorsWithCanonicalPaths() throws Exception {
        register("resize", "resize-1.0.0.json");
        String json = definition("{\"id\":\"resize\",\"processor\":\"resize\",\"version\":\"1.0.0\",\"config\":{\"widht\":800}}")
                .replace("\"kind\":\"image\"", "\"kind\":\"document\"")
                .replace("\"image/jpeg\"", "\"application/pdf\"");
        var report = validator.validateDefinition("product-images", mapper.readTree(json));

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.path().startsWith("/steps/0/config")));
        assertTrue(report.errors().stream().anyMatch(error -> error.code().equals("processor_input_incompatible")));
    }

    private void register(String slug, String resource) {
        Processor processor = mock(Processor.class);
        var manifest = ProcessorManifestResources.load(resource);
        when(processor.getMetadata()).thenReturn(new ProcessorMetadata(slug, manifest.input().mimeTypes(), (input, config) -> input, manifest));
        when(processor.getManifest()).thenReturn(manifest);
        when(registry.get(slug, "1.0.0")).thenReturn(processor);
    }

    private String definition(String steps) {
        return """
                {"schemaVersion":"1","slug":"product-images",
                 "input":{"kind":"image","mimeTypes":["image/jpeg"],"maxBytes":50000000,"maxPixels":40000000},
                 "steps":[%s],"limits":{"maxSteps":10}}
                """.formatted(steps);
    }
}

package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.MediaContract;
import com.sluice.api.pipeline.MediaTypeMatcher;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.validation.ProcessorConfigurationException;
import com.sluice.api.pipeline.validation.ProcessorConfigurationValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PipelineValidator {
    private static final int MAX_STEPS = 10;
    private static final Pattern STEP_ID = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

    private final ProcessorRegistry processorRegistry;
    private final ProcessorConfigurationValidator configurationValidator;

    public PipelineValidator(ProcessorRegistry processorRegistry,
                             ProcessorConfigurationValidator configurationValidator) {
        this.processorRegistry = processorRegistry;
        this.configurationValidator = configurationValidator;
    }

    public PipelineValidationReport validateDefinition(String pipelineSlug, JsonNode definition) {
        List<PipelineValidationError> errors = new ArrayList<>();
        if (definition == null || !definition.isObject()) {
            errors.add(error("/", "definition_invalid", "Pipeline definition must be a JSON object."));
            return new PipelineValidationReport(false, errors, null, null);
        }
        if (!"1".equals(definition.path("schemaVersion").asText())) {
            errors.add(error("/schemaVersion", "schema_version_invalid", "schemaVersion must be '1'."));
        }
        if (!pipelineSlug.equals(definition.path("slug").asText())) {
            errors.add(error("/slug", "slug_mismatch", "Definition slug must match the pipeline slug."));
        }

        MediaContract input = readContract(definition.get("input"), "/input", errors);
        MediaContract current = input;
        JsonNode steps = definition.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            errors.add(error("/steps", "steps_required", "At least one ordered step is required."));
        } else if (steps.size() > MAX_STEPS) {
            errors.add(error("/steps", "step_limit_exceeded", "Pipelines may contain at most 10 steps."));
        }

        Set<String> stepIds = new HashSet<>();
        if (steps != null && steps.isArray()) {
            for (int index = 0; index < steps.size(); index++) {
                JsonNode step = steps.get(index);
                String path = "/steps/" + index;
                String id = step.path("id").asText();
                if (!STEP_ID.matcher(id).matches()) {
                    errors.add(error(path + "/id", "step_id_invalid", "Step id must be a lowercase slug."));
                } else if (!stepIds.add(id)) {
                    errors.add(error(path + "/id", "step_id_duplicate", "Step ids must be unique."));
                }
                String slug = step.path("processor").asText();
                String version = step.path("version").asText();
                if (slug.isBlank()) {
                    errors.add(error(path + "/processor", "processor_required", "Processor slug is required."));
                    continue;
                }
                if (version.isBlank()) {
                    errors.add(error(path + "/version", "processor_version_required", "An exact processor version is required."));
                    continue;
                }

                Processor processor;
                try {
                    processor = processorRegistry.get(slug, version);
                } catch (IllegalArgumentException exception) {
                    errors.add(error(path + "/version", "processor_release_unknown", "The exact processor release is not available."));
                    continue;
                }
                ProcessorManifest manifest = processor.getManifest();
                if (!Set.of("PUBLISHED", "DEPRECATED").contains(manifest.status())) {
                    errors.add(error(path + "/version", "processor_release_unavailable", "The processor release cannot be used for publication."));
                    continue;
                }
                try {
                    configurationValidator.validate(manifest, step.has("config") ? step.get("config") : null);
                } catch (ProcessorConfigurationException exception) {
                    exception.getErrors().forEach(item -> errors.add(error(
                            path + "/config" + item.path(), item.code(), item.message())));
                }
                if (current != null && !accepts(manifest.input(), current)) {
                    errors.add(error(path, "processor_input_incompatible",
                            "Processor input contract is incompatible with the previous output."));
                } else if (current != null) {
                    current = resolveOutput(current, manifest.output());
                }
            }
        }

        JsonNode limits = definition.get("limits");
        if (limits != null && limits.isObject()) {
            int declaredMaxSteps = limits.path("maxSteps").asInt(MAX_STEPS);
            if (declaredMaxSteps < 1 || declaredMaxSteps > MAX_STEPS || (steps != null && steps.isArray() && steps.size() > declaredMaxSteps)) {
                errors.add(error("/limits/maxSteps", "max_steps_invalid", "maxSteps must be between the step count and 10."));
            }
        }
        return new PipelineValidationReport(errors.isEmpty(), errors, input, current);
    }

    public void validate(PipelineVersion version) {
        String slug = version.getPipeline() == null ? version.getDefinition().path("slug").asText() : version.getPipeline().getSlug();
        PipelineValidationReport report = validateDefinition(slug, version.getDefinition());
        if (!report.valid()) throw new PipelineValidationException(report);
    }

    private MediaContract readContract(JsonNode node, String path, List<PipelineValidationError> errors) {
        if (node == null || !node.isObject()) {
            errors.add(error(path, "input_contract_required", "An input media contract is required."));
            return null;
        }
        String kind = node.path("kind").asText();
        List<String> mimeTypes = new ArrayList<>();
        JsonNode mimeNode = node.get("mimeTypes");
        if (mimeNode != null && mimeNode.isArray()) mimeNode.forEach(value -> mimeTypes.add(value.asText()));
        if (kind.isBlank()) errors.add(error(path + "/kind", "media_kind_required", "Input media kind is required."));
        if (mimeTypes.isEmpty()) errors.add(error(path + "/mimeTypes", "mime_types_required", "At least one input MIME type is required."));
        if (kind.isBlank() || mimeTypes.isEmpty()) return null;
        try {
            return new MediaContract(kind, mimeTypes, node.path("maxBytes").asLong(0), node.path("maxPixels").asLong(0),
                    node.path("alphaSupported").asBoolean(false), node.path("animationSupported").asBoolean(false));
        } catch (IllegalArgumentException exception) {
            errors.add(error(path, "input_contract_invalid", exception.getMessage()));
            return null;
        }
    }

    private boolean accepts(MediaContract accepted, MediaContract actual) {
        boolean kindMatches = "media".equals(accepted.kind()) || accepted.kind().equals(actual.kind());
        return kindMatches && actual.mimeTypes().stream().allMatch(actualType ->
                accepted.mimeTypes().stream().anyMatch(expected -> MediaTypeMatcher.matches(actualType, expected)));
    }

    private MediaContract resolveOutput(MediaContract current, MediaContract output) {
        String kind = "media".equals(output.kind()) ? current.kind() : output.kind();
        List<String> mimeTypes = output.mimeTypes().stream().allMatch(type -> type.contains("*"))
                ? current.mimeTypes() : output.mimeTypes();
        return new MediaContract(kind, mimeTypes,
                output.maxBytes() == 0 ? current.maxBytes() : output.maxBytes(),
                output.maxPixels() == 0 ? current.maxPixels() : output.maxPixels(),
                output.alphaSupported(), output.animationSupported());
    }

    private PipelineValidationError error(String path, String code, String message) {
        return new PipelineValidationError(path, code, message);
    }
}

package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorRegistry;
import com.sluice.api.pipeline.domain.PipelineVersion;
import org.springframework.stereotype.Component;

@Component
public class PipelineValidator {

    private final ProcessorRegistry processorRegistry;

    public PipelineValidator(ProcessorRegistry processorRegistry) {
        this.processorRegistry = processorRegistry;
    }

    public void validate(PipelineVersion version) {
        String currentMimeType = version.getExpectedInputMimeType();
        JsonNode definition = version.getDefinition();

        if (definition == null || !definition.has("steps") || !definition.get("steps").isArray()) {
            throw new IllegalArgumentException("Pipeline definition must contain an array of 'steps'");
        }

        for (JsonNode stepNode : definition.get("steps")) {
            if (!stepNode.has("processor")) {
                throw new IllegalArgumentException("Each step must have a 'processor' name");
            }
            String processorName = stepNode.get("processor").asText();
            JsonNode config = stepNode.has("config") ? stepNode.get("config") : null;

            ProcessorMetadata metadata = processorRegistry.get(processorName).getMetadata();

            // Validate that the processor accepts the current MIME type
            String finalCurrentMimeType = currentMimeType;
            boolean accepts = metadata.acceptedMimeTypes().stream()
                    .anyMatch(accepted -> finalCurrentMimeType.startsWith(accepted.replace("*", "")) || "*/*".equals(accepted));

            if (!accepts && !currentMimeType.equals("*/*")) {
                throw new IllegalArgumentException(String.format("Processor '%s' does not accept MIME type '%s'", processorName, currentMimeType));
            }

            // Determine output MIME type
            currentMimeType = metadata.outputResolver().resolve(currentMimeType, config);
        }
    }
}

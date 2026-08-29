package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PipelineResolver {
    
    private final ProcessorRegistry processorRegistry;
    private final ProcessorVersionRepository processorVersions;

    public PipelineResolver(ProcessorRegistry processorRegistry) {
        this(processorRegistry, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PipelineResolver(ProcessorRegistry processorRegistry, ProcessorVersionRepository processorVersions) {
        this.processorRegistry = processorRegistry;
        this.processorVersions = processorVersions;
    }

    public Pipeline resolve(JsonNode definition) {
        List<ConfiguredStep> steps = new ArrayList<>();
        
        if (definition != null && definition.has("steps") && definition.get("steps").isArray()) {
            for (JsonNode stepNode : definition.get("steps")) {
                String processorName = stepNode.get("processor").asText();
                String stepId = stepNode.get("id").asText();
                JsonNode config = stepNode.has("config") ? stepNode.get("config") : null;
                
                Processor processor = stepNode.hasNonNull("version")
                        ? processorRegistry.get(processorName, stepNode.get("version").asText())
                        : processorRegistry.get(processorName);
                boolean globallyDisabled = "DISABLED".equals(processor.getManifest().status());
                if (!globallyDisabled && processorVersions != null) {
                    globallyDisabled = processorVersions.findByDefinitionSlugAndSemanticVersion(
                                    processorName, stepNode.path("version").asText())
                            .map(version -> "DISABLED".equals(version.getLifecycleStatus()))
                            .orElse(false);
                }
                if (globallyDisabled) {
                    throw new IllegalStateException("Processor release is globally disabled: "
                            + processor.getManifest().key());
                }
                steps.add(new ConfiguredStep(stepId, processor, config));
            }
        }
        
        return new Pipeline(steps);
    }
}

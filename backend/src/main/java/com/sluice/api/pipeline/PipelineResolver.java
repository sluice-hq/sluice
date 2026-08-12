package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PipelineResolver {
    
    private final ProcessorRegistry processorRegistry;

    public PipelineResolver(ProcessorRegistry processorRegistry) {
        this.processorRegistry = processorRegistry;
    }

    public Pipeline resolve(JsonNode definition) {
        List<ConfiguredStep> steps = new ArrayList<>();
        
        if (definition != null && definition.has("steps") && definition.get("steps").isArray()) {
            for (JsonNode stepNode : definition.get("steps")) {
                String processorName = stepNode.get("processor").asText();
                JsonNode config = stepNode.has("config") ? stepNode.get("config") : null;
                
                Processor processor = processorRegistry.get(processorName);
                steps.add(new ConfiguredStep(processor, config));
            }
        }
        
        return new Pipeline(steps);
    }
}

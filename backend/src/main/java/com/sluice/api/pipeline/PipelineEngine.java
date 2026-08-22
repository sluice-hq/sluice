package com.sluice.api.pipeline;

import org.springframework.stereotype.Service;

@Service
public class PipelineEngine {

    public void execute(Pipeline pipeline, ProcessingContext context) throws Exception {
        java.util.List<MediaResource> trackedResources = new java.util.ArrayList<>();
        if (context.getCurrentResource() != null) {
            trackedResources.add(context.getCurrentResource());
        }

        try {
            for (ConfiguredStep step : pipeline.getSteps()) {
                ProcessorResult result = step.getProcessor().process(context, step.getConfig());
                
                if (result != null) {
                    if (result.getMetadata() != null) {
                        context.getAttributes().putAll(result.getMetadata());
                    }
                    if (result.getNewResource().isPresent()) {
                        MediaResource newResource = result.getNewResource().get();
                        trackedResources.add(newResource);
                        context.setCurrentResource(newResource);
                    }
                }
            }
        } finally {
            for (MediaResource resource : trackedResources) {
                // The caller owns the final pipeline output and may still need to
                // persist it. Only intermediate resources are engine-owned.
                if (resource == context.getCurrentResource()) {
                    continue;
                }
                try {
                    resource.cleanup();
                } catch (Exception e) {
                    System.err.println("Failed to cleanup resource: " + e.getMessage());
                }
            }
        }
    }
}

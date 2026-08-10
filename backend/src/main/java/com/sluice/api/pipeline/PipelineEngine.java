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
            for (Processor processor : pipeline.getProcessors()) {
                ProcessorResult result = processor.process(context);
                
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
                try {
                    resource.cleanup();
                } catch (Exception e) {
                    System.err.println("Failed to cleanup resource: " + e.getMessage());
                }
            }
        }
    }
}

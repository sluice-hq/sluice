package com.sluice.api.pipeline;

import org.springframework.stereotype.Service;

@Service
public class PipelineEngine {

    public void execute(Pipeline pipeline, ProcessingContext context) throws Exception {
        execute(pipeline, context, StepExecutionListener.NO_OP);
    }

    public void execute(Pipeline pipeline, ProcessingContext context, StepExecutionListener listener) throws Exception {
        java.util.List<MediaResource> trackedResources = new java.util.ArrayList<>();
        if (context.getCurrentResource() != null) {
            trackedResources.add(context.getCurrentResource());
        }

        try {
            for (ConfiguredStep step : pipeline.getSteps()) {
                listener.beforeStep(step, context.getCurrentResource());
                try {
                    ProcessorResult result = step.getProcessor().process(context, step.getConfig());
                    boolean changed = false;
                    java.util.Map<String, Object> metadata = java.util.Map.of();
                    if (result != null) {
                        if (result.getMetadata() != null) {
                            metadata = result.getMetadata();
                            context.getAttributes().putAll(metadata);
                        }
                        if (result.getNewResource().isPresent()) {
                            MediaResource newResource = result.getNewResource().get();
                            trackedResources.add(newResource);
                            context.setCurrentResource(newResource);
                            changed = true;
                        }
                    }
                    listener.afterStep(step, context.getCurrentResource(), metadata, changed);
                    Object governanceDecision = metadata.get(
                            com.sluice.api.pipeline.processor.ContentSafetyProcessor.DECISION_FACT);
                    if ("REVIEW".equals(governanceDecision) || "BLOCK".equals(governanceDecision)) {
                        return;
                    }
                } catch (Exception exception) {
                    listener.onFailure(step, exception);
                    throw exception;
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

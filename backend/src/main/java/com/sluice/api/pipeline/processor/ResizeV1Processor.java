package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.image.ImageProcessingGuard;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResizeV1Processor extends ResizeProcessor {
    public ResizeV1Processor(ImageProcessingGuard guard) { super(guard); }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("resize", List.of("image/*"),
                (inputMimeType, config) -> "image/jpeg",
                ProcessorManifestResources.load("resize-1.0.0.json"));
    }

    @Override protected String widthKey() { return "width"; }
    @Override protected String heightKey() { return "height"; }
    @Override protected boolean allowUpscale(JsonNode config) { return true; }
    @Override protected String outputMime(String inputMime) { return "image/jpeg"; }
}

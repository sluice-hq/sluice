package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.image.ImageProcessingGuard;
import org.springframework.stereotype.Component;

import java.util.List;

/** Keeps already-published 1.0.0 pipelines executable while using the real pinned codec. */
@Component
public class WebpV1Processor extends WebpProcessor {
    public WebpV1Processor(ImageProcessingGuard guard) { super(guard); }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("webp", List.of("image/*"),
                (inputMimeType, config) -> "image/webp",
                ProcessorManifestResources.load("webp-1.0.0.json"));
    }
}

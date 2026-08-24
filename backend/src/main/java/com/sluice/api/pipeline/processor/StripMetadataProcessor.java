package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.image.ImageEncoding;
import com.sluice.api.pipeline.image.ImageProcessingGuard;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Component
public class StripMetadataProcessor implements Processor {
    private final ImageProcessingGuard guard;

    public StripMetadataProcessor(ImageProcessingGuard guard) { this.guard = guard; }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("strip-metadata", List.of("image/jpeg", "image/png", "image/webp"),
                (inputMimeType, config) -> inputMimeType,
                ProcessorManifestResources.load("strip-metadata-1.0.0.json"));
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        ImageProcessingGuard.DecodedImage decoded = guard.decode(context);
        File output = Files.createTempFile("sluice-stripped-",
                ImageEncoding.extension(decoded.mimeType())).toFile();
        long started = guard.startTimer();
        try {
            ImageEncoding.writeWithoutMetadata(decoded.image(), decoded.mimeType(), output);
            guard.validateOutput(output, started);
            return new ProcessorResult(new FileMediaResource(output, decoded.mimeType()), Map.of(
                    "metadataStripped", true,
                    "colorProfilePreserved", false,
                    "width", decoded.width(),
                    "height", decoded.height(),
                    "inputMimeType", decoded.mimeType(),
                    "outputMimeType", decoded.mimeType(),
                    "originalBytes", decoded.bytes(),
                    "finalBytes", output.length()));
        } catch (Exception exception) {
            Files.deleteIfExists(output.toPath());
            throw exception;
        }
    }
}

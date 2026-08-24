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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebpProcessor implements Processor {
    protected final ImageProcessingGuard guard;

    public WebpProcessor(ImageProcessingGuard guard) {
        this.guard = guard;
    }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("webp", List.of("image/jpeg", "image/png", "image/webp"),
                (inputMimeType, config) -> "image/webp",
                ProcessorManifestResources.load("webp-2.0.0.json"));
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        int quality = config != null && config.has("quality")
                ? config.path("quality").asInt() : ImageEncoding.DEFAULT_WEBP_QUALITY;
        ImageProcessingGuard.DecodedImage decoded = guard.decode(context);
        long started = guard.startTimer();
        File output = Files.createTempFile("sluice-webp-", ".webp").toFile();
        try {
            ImageEncoding.writeWebp(decoded.image(), output, quality);
            guard.validateOutput(output, started);
            Map<String, Object> facts = compressionFacts(decoded, output.length(), quality);
            return new ProcessorResult(new FileMediaResource(output, "image/webp"), facts);
        } catch (Exception exception) {
            Files.deleteIfExists(output.toPath());
            throw exception;
        }
    }

    protected Map<String, Object> compressionFacts(ImageProcessingGuard.DecodedImage input,
                                                     long outputBytes, int quality) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("originalBytes", input.bytes());
        facts.put("finalBytes", outputBytes);
        facts.put("bytesSaved", Math.max(0, input.bytes() - outputBytes));
        facts.put("compressionRatio", input.bytes() == 0 ? 0.0
                : Math.round(((double) outputBytes / input.bytes()) * 1_000_000d) / 1_000_000d);
        facts.put("inputMimeType", input.mimeType());
        facts.put("outputMimeType", "image/webp");
        facts.put("inputWidth", input.width());
        facts.put("inputHeight", input.height());
        facts.put("outputWidth", input.width());
        facts.put("outputHeight", input.height());
        facts.put("quality", quality);
        facts.put("metadataStripped", true);
        facts.put("codec", "com.github.usefulness:webp-imageio:0.11.0");
        return facts;
    }
}

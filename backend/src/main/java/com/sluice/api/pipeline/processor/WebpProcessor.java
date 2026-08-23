package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.FileMediaResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import java.util.List;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sluice.api.pipeline.ProcessorManifestResources;

@Component
public class WebpProcessor implements Processor {

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "webp",
            List.of("image/*"),
            (inputMimeType, config) -> "image/webp",
            ProcessorManifestResources.load("webp-1.0.0.json")
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        try (InputStream is = context.getCurrentResource().getInputStream()) {
            BufferedImage originalImage = ImageIO.read(is);
            if (originalImage == null) {
                throw new IllegalArgumentException("WebP input is not a readable image");
            }

            File tempFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".webp");
            boolean wrote = ImageIO.write(originalImage, "webp", tempFile);
            if (!wrote) {
                java.nio.file.Files.deleteIfExists(tempFile.toPath());
                throw new IllegalStateException(
                        "WebP processing is unavailable because no WebP ImageIO writer is installed");
            }
            
            System.out.println("Successfully converted image for Job " + context.getJob().getId());

            return new ProcessorResult(new FileMediaResource(tempFile, "image/webp"),
                    Map.of("webpConverted", true));
        }
    }
}

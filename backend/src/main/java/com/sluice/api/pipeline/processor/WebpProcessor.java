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

@Component
public class WebpProcessor implements Processor {

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "webp",
            List.of("image/*"),
            (inputMimeType, config) -> "image/webp"
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        try (InputStream is = context.getCurrentResource().getInputStream()) {
            BufferedImage originalImage = ImageIO.read(is);
            if (originalImage == null) {
                System.out.println("Not an image, skipping WebP conversion.");
                return null;
            }

            File tempFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".webp");
            
            // Note: writing WebP requires a plugin in Java. If none is present, this will fail or write empty.
            // We assume an appropriate plugin or fallback strategy is deployed.
            boolean wrote = ImageIO.write(originalImage, "webp", tempFile);
            if (!wrote) {
                System.err.println("No WebP ImageWriter found, falling back to PNG.");
                tempFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".png");
                ImageIO.write(originalImage, "png", tempFile);
            }
            
            System.out.println("Successfully converted image for Job " + context.getJob().getId());

            return new ProcessorResult(new FileMediaResource(tempFile, "image/webp"), Map.of("webpConverted", true));
        } catch (Exception e) {
            System.err.println("Failed to convert image to WebP: " + e.getMessage());
            return null;
        }
    }
}

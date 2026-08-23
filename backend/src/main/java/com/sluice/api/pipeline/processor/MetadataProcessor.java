package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.sluice.api.pipeline.ProcessorResult;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import java.util.List;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sluice.api.pipeline.ProcessorManifestResources;

@Component
public class MetadataProcessor implements Processor {

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "metadata",
            List.of("image/*"),
            (inputMimeType, config) -> inputMimeType,
            ProcessorManifestResources.load("metadata-1.0.0.json")
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        long fileSize = context.getCurrentResource().getSize();
        metadata.put("fileSize", fileSize);
        
        try (InputStream is = context.getCurrentResource().getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image != null) {
                metadata.put("width", image.getWidth());
                metadata.put("height", image.getHeight());
                System.out.println("Extracted Metadata for Job " + context.getJob().getId() + 
                                   ": Size=" + fileSize + " bytes, Dimensions=" + 
                                   image.getWidth() + "x" + image.getHeight());
            } else {
                System.out.println("Extracted Metadata for Job " + context.getJob().getId() + 
                                   ": Size=" + fileSize + " bytes (Not an image)");
            }
        } catch (Exception e) {
            System.err.println("Could not parse image metadata: " + e.getMessage());
        }
        
        return new ProcessorResult(null, metadata);
    }
}

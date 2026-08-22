package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.FileMediaResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import java.util.List;

@Component
public class ResizeProcessor implements Processor {

    private final int absoluteMaxWidth;
    private final int absoluteMaxHeight;

    public ResizeProcessor(
            @Value("${sluice.processor.resize.absoluteMaxWidth:5000}") int absoluteMaxWidth,
            @Value("${sluice.processor.resize.absoluteMaxHeight:5000}") int absoluteMaxHeight) {
        this.absoluteMaxWidth = absoluteMaxWidth;
        this.absoluteMaxHeight = absoluteMaxHeight;
    }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "resize",
            List.of("image/*"),
            (inputMimeType, config) -> "image/jpeg"
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        int targetWidth = config != null && config.has("width") ? config.get("width").asInt() : 200;
        int targetHeight = config != null && config.has("height") ? config.get("height").asInt() : 200;
        
        targetWidth = Math.min(targetWidth, absoluteMaxWidth);
        targetHeight = Math.min(targetHeight, absoluteMaxHeight);

        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Resize width and height must be positive");
        }

        try (InputStream is = context.getCurrentResource().getInputStream()) {
            BufferedImage originalImage = ImageIO.read(is);
            if (originalImage == null) {
                throw new IllegalArgumentException("Resize input is not a readable image");
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            double ratio = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
            int finalWidth = (int) (originalWidth * ratio);
            int finalHeight = (int) (originalHeight * ratio);

            Image resultingImage = originalImage.getScaledInstance(finalWidth, finalHeight, Image.SCALE_SMOOTH);
            BufferedImage outputImage = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();

            File tempFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".jpg");
            ImageIO.write(outputImage, "jpg", tempFile);
            
            System.out.println("Successfully resized image for Job " + context.getJob().getId() + 
                               ". New dimensions: " + targetWidth + "x" + targetHeight);

            return new ProcessorResult(new FileMediaResource(tempFile, "image/jpeg"), Map.of("resized", true));
        }
    }
}

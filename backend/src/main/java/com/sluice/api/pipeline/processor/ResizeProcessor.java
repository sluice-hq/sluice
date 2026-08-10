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

@Component
public class ResizeProcessor implements Processor {

    private final int maxWidth;
    private final int maxHeight;

    public ResizeProcessor(
            @Value("${sluice.pipeline.resize.maxWidth:200}") int maxWidth,
            @Value("${sluice.pipeline.resize.maxHeight:200}") int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    @Override
    public ProcessorResult process(ProcessingContext context) throws Exception {
        try (InputStream is = context.getCurrentResource().getInputStream()) {
            BufferedImage originalImage = ImageIO.read(is);
            if (originalImage == null) {
                System.out.println("Not an image, skipping resize.");
                return null;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            double ratio = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);
            int targetWidth = (int) (originalWidth * ratio);
            int targetHeight = (int) (originalHeight * ratio);

            Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();

            File tempFile = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString() + ".jpg");
            ImageIO.write(outputImage, "jpg", tempFile);
            
            System.out.println("Successfully resized image for Job " + context.getJob().getId() + 
                               ". New dimensions: " + targetWidth + "x" + targetHeight);

            return new ProcessorResult(new FileMediaResource(tempFile, "image/jpeg"), Map.of("resized", true));

        } catch (Exception e) {
            System.err.println("Failed to resize image: " + e.getMessage());
            return null;
        }
    }
}

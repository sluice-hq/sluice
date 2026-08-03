package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Component
public class ThumbnailProcessor implements Processor {

    private static final int MAX_WIDTH = 200;
    private static final int MAX_HEIGHT = 200;

    @Override
    public void process(ProcessingContext context) throws Exception {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(context.getFileBytes()));
            if (originalImage == null) {
                System.out.println("Not an image, skipping thumbnail generation.");
                return;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            double ratio = Math.min((double) MAX_WIDTH / originalWidth, (double) MAX_HEIGHT / originalHeight);
            int targetWidth = (int) (originalWidth * ratio);
            int targetHeight = (int) (originalHeight * ratio);

            Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "jpg", baos);
            byte[] thumbnailBytes = baos.toByteArray();
            
            context.getAttributes().put("thumbnailBytesSize", thumbnailBytes.length);
            System.out.println("Successfully generated thumbnail for Job " + context.getJob().getId() + 
                               ". Thumbnail size: " + thumbnailBytes.length + " bytes.");

        } catch (Exception e) {
            System.err.println("Failed to generate thumbnail: " + e.getMessage());
        }
    }
}

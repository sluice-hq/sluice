package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Component
public class MetadataProcessor implements Processor {
    @Override
    public void process(ProcessingContext context) throws Exception {
        byte[] fileBytes = context.getFileBytes();
        context.getAttributes().put("fileSize", fileBytes.length);
        
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (image != null) {
                context.getAttributes().put("width", image.getWidth());
                context.getAttributes().put("height", image.getHeight());
                System.out.println("Extracted Metadata for Job " + context.getJob().getId() + 
                                   ": Size=" + fileBytes.length + " bytes, Dimensions=" + 
                                   image.getWidth() + "x" + image.getHeight());
            } else {
                System.out.println("Extracted Metadata for Job " + context.getJob().getId() + 
                                   ": Size=" + fileBytes.length + " bytes (Not an image)");
            }
        } catch (Exception e) {
            System.err.println("Could not parse image metadata: " + e.getMessage());
        }
    }
}

package com.sluice.api.pipeline.image;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WebpCodecCapability {
    @PostConstruct
    public void verify() {
        Path output = null;
        try {
            ImageIO.scanForPlugins();
            output = Files.createTempFile("sluice-webp-capability-", ".webp");
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0x80ff0000);
            ImageEncoding.writeWebp(image, output.toFile(), ImageEncoding.DEFAULT_WEBP_QUALITY);
            BufferedImage decoded = ImageIO.read(output.toFile());
            if (decoded == null || decoded.getWidth() != 2 || decoded.getHeight() != 2) {
                throw new IllegalStateException("WebP codec failed its startup encode/decode check");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Pinned WebP codec is unavailable in this runtime", exception);
        } finally {
            if (output != null) {
                try { Files.deleteIfExists(output); } catch (Exception ignored) { }
            }
        }
    }
}

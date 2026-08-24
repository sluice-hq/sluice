package com.sluice.api.pipeline.image;

import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class ImageProcessingGuard {
    private final long maxInputBytes;
    private final long maxOutputBytes;
    private final long maxPixels;
    private final int maxDimension;
    private final long timeoutMillis;

    public ImageProcessingGuard(
            @Value("${sluice.processor.image.max-input-bytes:50000000}") long maxInputBytes,
            @Value("${sluice.processor.image.max-output-bytes:50000000}") long maxOutputBytes,
            @Value("${sluice.processor.image.max-pixels:40000000}") long maxPixels,
            @Value("${sluice.processor.image.max-dimension:10000}") int maxDimension,
            @Value("${sluice.processor.image.timeout-millis:30000}") long timeoutMillis) {
        if (maxInputBytes < 1 || maxOutputBytes < 1 || maxPixels < 1 || maxDimension < 1 || timeoutMillis < 1) {
            throw new IllegalArgumentException("Image processing limits must be positive");
        }
        this.maxInputBytes = maxInputBytes;
        this.maxOutputBytes = maxOutputBytes;
        this.maxPixels = maxPixels;
        this.maxDimension = maxDimension;
        this.timeoutMillis = timeoutMillis;
    }

    public DecodedImage decode(ProcessingContext context) throws Exception {
        MediaResource resource = context.getCurrentResource();
        long inputBytes = resource.getSize();
        if (inputBytes < 1 || inputBytes > maxInputBytes) {
            throw new IllegalArgumentException("Image input exceeds the configured byte limit");
        }
        long started = System.nanoTime();
        try (InputStream input = resource.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) throw new IllegalArgumentException("Image input cannot be inspected");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException("Image input is not a supported image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                int frameCount = reader.getNumImages(true);
                if (frameCount > 1) throw new IllegalArgumentException("Animated images are not supported");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Image input cannot be decoded");
                checkTime(started);
                return new DecodedImage(image, width, height, inputBytes, contentType(context));
            } finally {
                reader.dispose();
            }
        }
    }

    public void validateOutput(java.io.File file, long startedNanos) {
        if (file.length() < 1 || file.length() > maxOutputBytes) {
            throw new IllegalArgumentException("Image output exceeds the configured byte limit");
        }
        checkTime(startedNanos);
    }

    public long startTimer() { return System.nanoTime(); }

    private void validateDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > maxDimension || height > maxDimension
                || (long) width * height > maxPixels) {
            throw new IllegalArgumentException("Image dimensions exceed the configured safety limits");
        }
    }

    private void checkTime(long startedNanos) {
        long elapsedMillis = java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        if (elapsedMillis > timeoutMillis) {
            throw new IllegalArgumentException("Image processing exceeded the configured time limit");
        }
    }

    private String contentType(ProcessingContext context) {
        if (context.getCurrentResource() instanceof FileMediaResource file && file.getContentType() != null) {
            return file.getContentType();
        }
        return context.getAsset() == null ? null : context.getAsset().getContentType();
    }

    public record DecodedImage(BufferedImage image, int width, int height, long bytes, String mimeType) {}
}

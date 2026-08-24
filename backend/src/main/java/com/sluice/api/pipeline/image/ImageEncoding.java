package com.sluice.api.pipeline.image;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

public final class ImageEncoding {
    public static final int DEFAULT_WEBP_QUALITY = 82;
    public static final int MIN_WEBP_QUALITY = 40;
    public static final int MAX_WEBP_QUALITY = 95;

    private ImageEncoding() {}

    public static void writeWebp(BufferedImage image, File output, int quality) throws Exception {
        if (quality < MIN_WEBP_QUALITY || quality > MAX_WEBP_QUALITY) {
            throw new IllegalArgumentException("WebP quality must be between 40 and 95");
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) throw new IllegalStateException("WebP ImageIO writer is unavailable");
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
            param.setCompressionType(CompressionType.Lossy);
            param.setCompressionQuality(quality / 100.0f);
            param.setMethod(6);
            param.setThreadLevel(0);
            param.setExact(true);
            param.setUseSharpYUV(true);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public static void writeWithoutMetadata(BufferedImage image, String mimeType, File output) throws Exception {
        if ("image/webp".equals(mimeType)) {
            writeWebp(image, output, DEFAULT_WEBP_QUALITY);
            return;
        }
        String format = switch (mimeType) {
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException("Metadata stripping does not support " + mimeType);
        };
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IllegalStateException("Image writer is unavailable for " + mimeType);
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("image/jpeg".equals(mimeType) && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public static String extension(String mimeType) {
        return switch (mimeType) {
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("Unsupported output MIME type: " + mimeType);
        };
    }
}

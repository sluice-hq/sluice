package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.image.ImageProcessingGuard;
import com.sluice.api.pipeline.image.WebpCodecCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RealImageProcessorTest {
    @TempDir Path temporaryDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pinnedWebpCodecProducesRepeatableTransparentOutputAndCompressionFacts() throws Exception {
        try (InputStream linuxX64 = WebpProcessor.class.getResourceAsStream(
                "/native/Linux/x86_64/libwebp-imageio.so");
             InputStream linuxArm64 = WebpProcessor.class.getResourceAsStream(
                     "/native/Linux/aarch64/libwebp-imageio.so")) {
            assertNotNull(linuxX64);
            assertNotNull(linuxArm64);
        }
        new WebpCodecCapability().verify();
        Path input = transparentPng(24, 12);
        WebpProcessor processor = new WebpProcessor(guard());

        ProcessorResult first = processor.process(context(input, "image/png"), mapper.readTree("{\"quality\":82}"));
        ProcessorResult second = processor.process(context(input, "image/png"), mapper.readTree("{\"quality\":82}"));
        FileMediaResource firstOutput = (FileMediaResource) first.getNewResource().orElseThrow();
        FileMediaResource secondOutput = (FileMediaResource) second.getNewResource().orElseThrow();
        try {
            byte[] bytes = Files.readAllBytes(firstOutput.getFile().toPath());
            assertArrayEquals(bytes, Files.readAllBytes(secondOutput.getFile().toPath()));
            assertEquals("RIFF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WEBP", new String(bytes, 8, 4, StandardCharsets.US_ASCII));
            BufferedImage decoded = ImageIO.read(firstOutput.getFile());
            assertEquals(24, decoded.getWidth());
            assertEquals(12, decoded.getHeight());
            assertTrue(decoded.getColorModel().hasAlpha());
            assertTrue((decoded.getRGB(0, 0) >>> 24) < 255);
            assertEquals("image/webp", firstOutput.getContentType());
            assertEquals(82, first.getMetadata().get("quality"));
            assertEquals("image/png", first.getMetadata().get("inputMimeType"));
            assertEquals("image/webp", first.getMetadata().get("outputMimeType"));
            assertEquals(24, first.getMetadata().get("outputWidth"));
            assertNotNull(first.getMetadata().get("bytesSaved"));
        } finally {
            firstOutput.cleanup();
            secondOutput.cleanup();
        }
    }

    @Test
    void twoComponentPngEncodesToWebp() throws Exception {
        Path input = temporaryDirectory.resolve("demo.png");
        Files.write(input, Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
        ProcessingContext context = context(input, "image/png");
        ProcessorResult encoded = new WebpProcessor(guard()).process(context,
                mapper.readTree("{\"quality\":82}"));
        FileMediaResource webpOutput = (FileMediaResource) encoded.getNewResource().orElseThrow();
        try {
            assertEquals("image/webp", webpOutput.getContentType());
            assertNotNull(ImageIO.read(webpOutput.getFile()));
        } finally {
            webpOutput.cleanup();
        }
    }

    @Test
    void resizePreservesAspectRatioAlphaAndRefusesUnboundedInput() throws Exception {
        Path input = transparentPng(400, 200);
        ResizeProcessor processor = new ResizeProcessor(guard());
        ProcessorResult result = processor.process(context(input, "image/png"),
                mapper.readTree("{\"maxWidth\":100,\"maxHeight\":100,\"allowUpscale\":false}"));
        FileMediaResource output = (FileMediaResource) result.getNewResource().orElseThrow();
        try {
            BufferedImage decoded = ImageIO.read(output.getFile());
            assertEquals(100, decoded.getWidth());
            assertEquals(50, decoded.getHeight());
            assertTrue(decoded.getColorModel().hasAlpha());
            assertEquals("image/png", output.getContentType());
        } finally {
            output.cleanup();
        }

        ImageProcessingGuard tinyLimit = new ImageProcessingGuard(10, 1_000_000, 1_000_000, 1000, 30_000);
        assertThrows(IllegalArgumentException.class,
                () -> new ResizeProcessor(tinyLimit).process(context(input, "image/png"), null));
    }

    @Test
    void stripMetadataRemovesExifPayloadAndKeepsMimeAndDimensions() throws Exception {
        Path input = jpegWithExif(40, 20);
        assertTrue(new String(Files.readAllBytes(input), StandardCharsets.ISO_8859_1).contains("Exif"));
        ProcessorResult result = new StripMetadataProcessor(guard()).process(context(input, "image/jpeg"), null);
        FileMediaResource output = (FileMediaResource) result.getNewResource().orElseThrow();
        try {
            byte[] clean = Files.readAllBytes(output.getFile().toPath());
            assertFalse(new String(clean, StandardCharsets.ISO_8859_1).contains("Exif"));
            BufferedImage decoded = ImageIO.read(output.getFile());
            assertEquals(40, decoded.getWidth());
            assertEquals(20, decoded.getHeight());
            assertEquals("image/jpeg", output.getContentType());
            assertEquals(false, result.getMetadata().get("colorProfilePreserved"));
        } finally {
            output.cleanup();
        }
    }

    private ImageProcessingGuard guard() {
        return new ImageProcessingGuard(5_000_000, 5_000_000, 2_000_000, 2000, 30_000);
    }

    private ProcessingContext context(Path path, String mime) {
        UUID project = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), assetId, JobStatus.RUNNING, Instant.now(), Instant.now(), project);
        Asset asset = new Asset(assetId, path.getFileName().toString(), path.toFile().length(), mime,
                path.toString(), Asset.UploadStatus.COMPLETED, Instant.now(), project);
        return new ProcessingContext(job, asset, new FileMediaResource(path.toFile(), mime));
    }

    private Path transparentPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(30, 120, 220, 90));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        Path path = temporaryDirectory.resolve("transparent-" + width + "x" + height + ".png");
        assertTrue(ImageIO.write(image, "png", path.toFile()));
        return path;
    }

    private Path jpegWithExif(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        Path base = temporaryDirectory.resolve("base.jpg");
        assertTrue(ImageIO.write(image, "jpeg", base.toFile()));
        byte[] jpeg = Files.readAllBytes(base);
        byte[] payload = "Exif\0\0GPS:12.34;Camera:SluiceTest".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        byte[] segment = new byte[payload.length + 4];
        segment[0] = (byte) 0xff; segment[1] = (byte) 0xe1;
        segment[2] = (byte) (length >>> 8); segment[3] = (byte) length;
        System.arraycopy(payload, 0, segment, 4, payload.length);
        byte[] withExif = new byte[jpeg.length + segment.length];
        System.arraycopy(jpeg, 0, withExif, 0, 2);
        System.arraycopy(segment, 0, withExif, 2, segment.length);
        System.arraycopy(jpeg, 2, withExif, 2 + segment.length, jpeg.length - 2);
        Path path = temporaryDirectory.resolve("with-exif.jpg");
        Files.write(path, withExif);
        return path;
    }
}

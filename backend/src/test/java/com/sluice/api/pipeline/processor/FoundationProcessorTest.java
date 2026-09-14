package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.ProcessorResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationProcessorTest {
    @TempDir Path temporaryDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mimeValidationAcceptsExactAndWildcardTypesAndRejectsMismatches() throws Exception {
        Path png = image("mime.png", 3, 2);
        MimeValidationProcessor processor = new MimeValidationProcessor();

        ProcessorResult exact = processor.process(context(png, "image/png"),
                mapper.readTree("{\"allowedTypes\":[\"image/png\"]}"));
        ProcessorResult caseInsensitiveExact = processor.process(context(png, "image/png"),
                mapper.readTree("{\"allowedTypes\":[\"IMAGE/PNG\"]}"));
        ProcessorResult wildcard = processor.process(context(png, "image/png"),
                mapper.readTree("{\"allowedTypes\":[\"image/*\"]}"));
        ProcessorResult defaultImagePolicy = processor.process(context(png, "image/png"), null);

        assertEquals("image/png", exact.getMetadata().get("validatedMimeType"));
        assertEquals("image/png", caseInsensitiveExact.getMetadata().get("validatedMimeType"));
        assertEquals("image/png", wildcard.getMetadata().get("validatedMimeType"));
        assertEquals("image/png", defaultImagePolicy.getMetadata().get("validatedMimeType"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(context(png, "image/png"),
                mapper.readTree("{\"allowedTypes\":[\"image/jpeg\"]}")));
        assertThrows(IllegalArgumentException.class, () -> processor.process(context(png, "image/png"),
                mapper.readTree("{\"allowedTypes\":[\"image/p\"]}")));
    }

    @Test
    void metadataReturnsImageFactsAndRejectsUnreadableImages() throws Exception {
        Path png = image("metadata.png", 7, 5);
        ProcessorResult result = new MetadataProcessor().process(context(png, "image/png"), null);

        assertEquals(Files.size(png), result.getMetadata().get("fileSize"));
        assertEquals(7, result.getMetadata().get("width"));
        assertEquals(5, result.getMetadata().get("height"));

        Path invalid = temporaryDirectory.resolve("invalid.png");
        Files.writeString(invalid, "not an image", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> new MetadataProcessor().process(context(invalid, "image/png"), null));
    }

    @Test
    void checksumProducesKnownSha256WithoutReplacingTheResource() throws Exception {
        Path input = temporaryDirectory.resolve("checksum.txt");
        Files.writeString(input, "abc", StandardCharsets.UTF_8);

        ProcessorResult result = new ChecksumProcessor().process(context(input, "text/plain"), null);

        assertTrue(result.getNewResource().isEmpty());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                result.getMetadata().get("checksum"));
    }

    private Path image(String filename, int width, int height) throws Exception {
        Path path = temporaryDirectory.resolve(filename);
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", path.toFile()));
        return path;
    }

    private ProcessingContext context(Path path, String mime) {
        UUID project = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), assetId, JobStatus.RUNNING, Instant.now(), Instant.now(), project);
        Asset asset = new Asset(assetId, path.getFileName().toString(), path.toFile().length(), mime,
                path.toString(), Asset.UploadStatus.COMPLETED, Instant.now(), project);
        return new ProcessingContext(job, asset, new FileMediaResource(path.toFile(), mime));
    }
}

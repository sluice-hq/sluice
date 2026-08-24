package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.image.ImageEncoding;
import com.sluice.api.pipeline.image.ImageProcessingGuard;
import org.springframework.stereotype.Component;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResizeProcessor implements Processor {
    protected final ImageProcessingGuard guard;

    public ResizeProcessor(ImageProcessingGuard guard) { this.guard = guard; }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("resize", List.of("image/jpeg", "image/png", "image/webp"),
                (inputMimeType, config) -> inputMimeType,
                ProcessorManifestResources.load("resize-2.0.0.json"));
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        ImageProcessingGuard.DecodedImage decoded = guard.decode(context);
        int maxWidth = intConfig(config, widthKey(), defaultWidth());
        int maxHeight = intConfig(config, heightKey(), defaultHeight());
        boolean allowUpscale = allowUpscale(config);
        if (maxWidth < 1 || maxHeight < 1 || maxWidth > 5000 || maxHeight > 5000) {
            throw new IllegalArgumentException("Resize bounds must be between 1 and 5000 pixels");
        }
        double scale = Math.min((double) maxWidth / decoded.width(), (double) maxHeight / decoded.height());
        if (!allowUpscale) scale = Math.min(1.0d, scale);
        int width = Math.max(1, (int) Math.round(decoded.width() * scale));
        int height = Math.max(1, (int) Math.round(decoded.height() * scale));
        String outputMime = outputMime(decoded.mimeType());

        BufferedImage resized = render(decoded.image(), width, height, outputMime);
        File output = Files.createTempFile("sluice-resize-", ImageEncoding.extension(outputMime)).toFile();
        long started = guard.startTimer();
        try {
            ImageEncoding.writeWithoutMetadata(resized, outputMime, output);
            guard.validateOutput(output, started);
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("originalBytes", decoded.bytes());
            facts.put("finalBytes", output.length());
            facts.put("inputMimeType", decoded.mimeType());
            facts.put("outputMimeType", outputMime);
            facts.put("inputWidth", decoded.width());
            facts.put("inputHeight", decoded.height());
            facts.put("outputWidth", width);
            facts.put("outputHeight", height);
            facts.put("allowUpscale", allowUpscale);
            facts.put("metadataStripped", true);
            return new ProcessorResult(new FileMediaResource(output, outputMime), facts);
        } catch (Exception exception) {
            Files.deleteIfExists(output.toPath());
            throw exception;
        }
    }

    protected String widthKey() { return "maxWidth"; }
    protected String heightKey() { return "maxHeight"; }
    protected int defaultWidth() { return 200; }
    protected int defaultHeight() { return 200; }
    protected boolean allowUpscale(JsonNode config) {
        return config != null && config.path("allowUpscale").asBoolean(false);
    }
    protected String outputMime(String inputMime) {
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(inputMime)) {
            throw new IllegalArgumentException("Resize does not support " + inputMime);
        }
        return inputMime;
    }

    private int intConfig(JsonNode config, String name, int fallback) {
        return config != null && config.has(name) ? config.path(name).asInt() : fallback;
    }

    private BufferedImage render(BufferedImage source, int width, int height, String outputMime) {
        boolean alpha = !"image/jpeg".equals(outputMime) && source.getColorModel().hasAlpha();
        BufferedImage output = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            if (!alpha) {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }
}

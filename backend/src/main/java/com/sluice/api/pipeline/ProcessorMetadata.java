package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Runtime compatibility facade retained for existing processors and pipeline code. */
public final class ProcessorMetadata {
    private final String name;
    private final List<String> acceptedMimeTypes;
    private final OutputMimeTypeResolver outputResolver;
    private final ProcessorManifest manifest;

    public ProcessorMetadata(String name, List<String> acceptedMimeTypes, OutputMimeTypeResolver outputResolver) {
        this(name, acceptedMimeTypes, outputResolver, defaultManifest(name, acceptedMimeTypes));
    }

    public ProcessorMetadata(String name, List<String> acceptedMimeTypes, OutputMimeTypeResolver outputResolver,
                             ProcessorManifest manifest) {
        this.name = name;
        this.acceptedMimeTypes = List.copyOf(acceptedMimeTypes);
        this.outputResolver = outputResolver;
        this.manifest = manifest;
    }

    public String name() { return name; }
    public List<String> acceptedMimeTypes() { return acceptedMimeTypes; }
    public OutputMimeTypeResolver outputResolver() { return outputResolver; }
    public ProcessorManifest manifest() { return manifest; }

    public static ProcessorManifest builtInManifest(String name, String displayName, String category,
                                                    List<String> inputMimeTypes, List<String> outputMimeTypes,
                                                    JsonNode configSchema) {
        return ProcessorManifestResources.load(name + "-1.0.0.json");
    }

    private static ProcessorManifest defaultManifest(String name, List<String> acceptedMimeTypes) {
        MediaContract input = MediaContract.any("media", acceptedMimeTypes);
        MediaContract output = MediaContract.any("media", List.of("*/*"));
        return new ProcessorManifest("1", name, "1.0.0", name, "Built-in Sluice processor", "foundation",
                input, output, JsonNodeFactory.instance.objectNode(), new ProcessorLimits(30, 512, 50_000_000),
                List.of("blob.read.input", "blob.write.output"), "PUBLISHED", "Initial built-in release");
    }
}

package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads the reviewed manifest fixture shipped beside each built-in implementation. */
public final class ProcessorManifestResources {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, ProcessorManifest> CACHE = new ConcurrentHashMap<>();

    private ProcessorManifestResources() {
    }

    public static ProcessorManifest load(String resourceName) {
        return CACHE.computeIfAbsent(resourceName, ProcessorManifestResources::read);
    }

    private static ProcessorManifest read(String resourceName) {
        String path = "/processors/" + resourceName;
        try (InputStream input = ProcessorManifestResources.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Processor manifest resource is missing: " + path);
            }
            return OBJECT_MAPPER.readValue(input, ProcessorManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Processor manifest resource is invalid: " + path, exception);
        }
    }
}

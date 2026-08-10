package com.sluice.api.pipeline;

import java.util.Map;
import java.util.Optional;

public class ProcessorResult {
    private final MediaResource newResource;
    private final Map<String, Object> metadata;

    public ProcessorResult(MediaResource newResource, Map<String, Object> metadata) {
        this.newResource = newResource;
        this.metadata = metadata;
    }

    public Optional<MediaResource> getNewResource() {
        return Optional.ofNullable(newResource);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

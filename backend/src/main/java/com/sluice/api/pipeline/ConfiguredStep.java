package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

public class ConfiguredStep {
    private final String id;
    private final Processor processor;
    private final JsonNode config;

    public ConfiguredStep(Processor processor, JsonNode config) {
        this(processor.getMetadata().name(), processor, config);
    }

    public ConfiguredStep(String id, Processor processor, JsonNode config) {
        this.id = id;
        this.processor = processor;
        this.config = config;
    }

    public String getId() { return id; }
    public Processor getProcessor() { return processor; }
    public JsonNode getConfig() { return config; }
}

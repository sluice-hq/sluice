package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

public class ConfiguredStep {
    private final Processor processor;
    private final JsonNode config;

    public ConfiguredStep(Processor processor, JsonNode config) {
        this.processor = processor;
        this.config = config;
    }

    public Processor getProcessor() { return processor; }
    public JsonNode getConfig() { return config; }
}

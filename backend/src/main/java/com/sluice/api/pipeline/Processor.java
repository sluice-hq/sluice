package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

public interface Processor {
    ProcessorMetadata getMetadata();
    ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception;
}

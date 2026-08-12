package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface OutputMimeTypeResolver {
    String resolve(String inputMimeType, JsonNode config);
}

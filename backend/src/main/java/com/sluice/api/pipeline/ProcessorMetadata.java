package com.sluice.api.pipeline;

import java.util.List;

public record ProcessorMetadata(
    String name,
    List<String> acceptedMimeTypes,
    OutputMimeTypeResolver outputResolver
) {}

package com.sluice.api.pipeline;

import java.util.List;

/** A bounded description of media a processor can consume or produce. */
public record MediaContract(
        String kind,
        List<String> mimeTypes,
        long maxBytes,
        long maxPixels,
        boolean alphaSupported,
        boolean animationSupported
) {
    public MediaContract {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("Media kind is required");
        if (mimeTypes == null || mimeTypes.isEmpty()) throw new IllegalArgumentException("At least one MIME type is required");
        mimeTypes = List.copyOf(mimeTypes);
        if (maxBytes < 0 || maxPixels < 0) throw new IllegalArgumentException("Media limits cannot be negative");
    }

    public boolean accepts(String mimeType) {
        return mimeType != null && mimeTypes.stream().anyMatch(accepted -> MediaTypeMatcher.matches(mimeType, accepted));
    }

    public static MediaContract any(String kind, List<String> mimeTypes) {
        return new MediaContract(kind, mimeTypes, 0, 0, false, false);
    }
}

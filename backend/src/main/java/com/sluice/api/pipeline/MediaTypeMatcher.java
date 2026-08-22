package com.sluice.api.pipeline;

import java.util.Locale;

/**
 * Shared, deliberately small MIME-type compatibility check for pipeline
 * validation and job submission. It supports exact types plus type wildcards
 * such as image/* and the universal wildcard *\/*.
 */
public final class MediaTypeMatcher {

    private MediaTypeMatcher() {
    }

    public static boolean matches(String actualMimeType, String acceptedMimeType) {
        if (actualMimeType == null || actualMimeType.isBlank()
                || acceptedMimeType == null || acceptedMimeType.isBlank()) {
            return false;
        }

        String actual = actualMimeType.toLowerCase(Locale.ROOT);
        String accepted = acceptedMimeType.toLowerCase(Locale.ROOT);

        if ("*/*".equals(accepted) || "*/*".equals(actual)) {
            return true;
        }
        if (accepted.endsWith("/*")) {
            return actual.startsWith(accepted.substring(0, accepted.length() - 1));
        }
        return actual.equals(accepted);
    }
}

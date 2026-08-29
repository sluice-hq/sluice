package com.sluice.api.asset.service;

import java.util.regex.Pattern;

/** Validates caller-owned correlation identifiers without treating them as identity or authorization data. */
public final class AssetReferencePolicy {
    public static final int SUBJECT_MAX_LENGTH = 128;
    public static final int REFERENCE_MAX_LENGTH = 255;
    public static final String ALLOWED_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:/-]*";
    private static final Pattern ALLOWED = Pattern.compile(ALLOWED_PATTERN);

    private AssetReferencePolicy() {}

    public static void validate(String externalSubjectId, String externalReference) {
        validateValue("externalSubjectId", externalSubjectId, SUBJECT_MAX_LENGTH);
        validateValue("externalReference", externalReference, REFERENCE_MAX_LENGTH);
    }

    private static void validateValue(String field, String value, int maxLength) {
        if (value == null) return;
        if (value.isBlank() || value.length() > maxLength || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be 1 to " + maxLength
                    + " characters, begin with a letter or number, and contain only letters, numbers, '.', '_', ':', '/', or '-'");
        }
    }
}

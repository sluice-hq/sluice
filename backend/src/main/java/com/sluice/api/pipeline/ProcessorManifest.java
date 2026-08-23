package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, API-visible identity and safety contract for one processor release. */
public record ProcessorManifest(
        String schemaVersion,
        String slug,
        String version,
        String displayName,
        String description,
        String category,
        MediaContract input,
        MediaContract output,
        JsonNode configSchema,
        ProcessorLimits limits,
        List<String> permissions,
        String status,
        String releaseNotes
) {
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");
    private static final Set<String> LIFECYCLE_STATUSES = Set.of(
            "DRAFT", "VALIDATING", "PUBLISHED", "DEPRECATED", "DISABLED");

    public ProcessorManifest {
        if (schemaVersion == null || schemaVersion.isBlank()) throw new IllegalArgumentException("Manifest schema version is required");
        if (slug == null || slug.isBlank() || version == null || version.isBlank()) throw new IllegalArgumentException("Processor identity is required");
        if (!SEMANTIC_VERSION.matcher(version).matches()) throw new IllegalArgumentException("Processor version must use semantic versioning");
        if (displayName == null || displayName.isBlank() || input == null || output == null || limits == null) {
            throw new IllegalArgumentException("Processor manifest is incomplete");
        }
        if (configSchema == null || !configSchema.isObject()) throw new IllegalArgumentException("Processor configuration schema must be an object");
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        status = status == null ? "PUBLISHED" : status;
        if (!LIFECYCLE_STATUSES.contains(status)) throw new IllegalArgumentException("Unknown processor lifecycle status");
        releaseNotes = releaseNotes == null ? "" : releaseNotes;
    }

    public String key() {
        return slug + "@" + version;
    }
}

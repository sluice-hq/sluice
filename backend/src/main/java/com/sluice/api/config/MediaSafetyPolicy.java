package com.sluice.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared upload boundary used by both the direct and SAS upload workflows. */
@Component
public class MediaSafetyPolicy {
    private final long maxBytes;
    private final int maxFilenameLength;
    private final Set<String> allowedContentTypes;

    public MediaSafetyPolicy(
            @Value("${sluice.media.max-upload-bytes:50000000}") long maxBytes,
            @Value("${sluice.media.max-filename-length:255}") int maxFilenameLength,
            @Value("${sluice.media.allowed-content-types:image/jpeg,image/png,image/gif,application/pdf,video/mp4}") String allowedContentTypes) {
        if (maxBytes < 1 || maxFilenameLength < 1) {
            throw new IllegalArgumentException("Media safety limits must be positive");
        }
        this.maxBytes = maxBytes;
        this.maxFilenameLength = maxFilenameLength;
        this.allowedContentTypes = Arrays.stream(allowedContentTypes.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedContentTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one media content type is required");
        }
    }

    public long maxBytes() { return maxBytes; }

    public Set<String> allowedContentTypes() { return allowedContentTypes; }

    public void validate(String filename, String contentType, long size) {
        if (filename == null || filename.isBlank()) {
            throw new MediaSafetyException(HttpStatus.BAD_REQUEST, "filename_required", "Filename is required");
        }
        if (filename.length() > maxFilenameLength || filename.contains("\u0000")) {
            throw new MediaSafetyException(HttpStatus.BAD_REQUEST, "invalid_filename", "Filename is too long or contains invalid characters");
        }
        if (size <= 0 || size > maxBytes) {
            throw new MediaSafetyException(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "File size exceeds the configured upload limit");
        }
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new MediaSafetyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Unsupported content type");
        }
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaSafetyException(HttpStatus.BAD_REQUEST, "empty_file", "File must not be empty");
        }
        validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
    }
}

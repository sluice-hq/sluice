package com.sluice.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaSafetyPolicyTest {
    private final MediaSafetyPolicy policy = new MediaSafetyPolicy(100, 20, "image/png,image/jpeg");

    @Test
    void acceptsConfiguredMediaWithinBounds() {
        assertDoesNotThrow(() -> policy.validate("photo.png", "image/png", 100));
    }

    @Test
    void rejectsOversizedOrUnsupportedRequests() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("photo.png", "image/png", 101));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("photo.gif", "image/gif", 10));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("a".repeat(21), "image/png", 10));
    }
}

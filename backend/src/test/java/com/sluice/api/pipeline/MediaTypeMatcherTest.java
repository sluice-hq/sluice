package com.sluice.api.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaTypeMatcherTest {

    @Test
    void supportsExactAndWildcardPipelineInputs() {
        assertTrue(MediaTypeMatcher.matches("image/png", "image/png"));
        assertTrue(MediaTypeMatcher.matches("image/png", "image/*"));
        assertTrue(MediaTypeMatcher.matches("application/pdf", "*/*"));
        assertFalse(MediaTypeMatcher.matches("application/pdf", "image/*"));
        assertFalse(MediaTypeMatcher.matches(null, "image/*"));
    }
}

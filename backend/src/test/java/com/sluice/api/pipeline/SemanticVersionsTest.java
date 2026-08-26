package com.sluice.api.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionsTest {
    @Test
    void followsSemanticVersionPrecedence() {
        assertTrue(SemanticVersions.compare("10.0.0", "2.0.0") > 0);
        assertTrue(SemanticVersions.compare("1.0.0", "1.0.0-rc.1") > 0);
        assertTrue(SemanticVersions.compare("1.0.0-rc.10", "1.0.0-rc.2") > 0);
        assertTrue(SemanticVersions.compare("1.0.0-beta", "1.0.0-1") > 0);
        assertEquals(0, SemanticVersions.compare("1.0.0+build.2", "1.0.0+build.1"));
    }
}

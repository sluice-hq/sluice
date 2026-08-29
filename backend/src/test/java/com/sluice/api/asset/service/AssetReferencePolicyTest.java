package com.sluice.api.asset.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetReferencePolicyTest {
    @Test
    void acceptsAbsentAndOpaqueApplicationIdentifiers() {
        assertDoesNotThrow(() -> AssetReferencePolicy.validate(null, null));
        assertDoesNotThrow(() -> AssetReferencePolicy.validate("user_123", "profile/avatar-2026:08"));
    }

    @Test
    void rejectsBlankOversizedAndUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> AssetReferencePolicy.validate(" ", null));
        assertThrows(IllegalArgumentException.class, () -> AssetReferencePolicy.validate("a".repeat(129), null));
        assertThrows(IllegalArgumentException.class, () -> AssetReferencePolicy.validate(null, "a".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> AssetReferencePolicy.validate("user@example.com", null));
        assertThrows(IllegalArgumentException.class, () -> AssetReferencePolicy.validate(null, "secret value"));
    }
}

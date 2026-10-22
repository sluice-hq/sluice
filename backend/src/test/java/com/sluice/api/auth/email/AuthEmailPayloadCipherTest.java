package com.sluice.api.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.webhook.crypto.LocalDataKeyWrapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthEmailPayloadCipherTest {
    private final AuthEmailPayloadCipher cipher = new AuthEmailPayloadCipher(
            new LocalDataKeyWrapper("v1", Map.of("v1", new byte[32])), new ObjectMapper().findAndRegisterModules());

    @Test
    void ciphertextCanBeReopenedButTamperingOrSwappingRowsFails() {
        UUID id = UUID.randomUUID();
        var email = new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.PASSWORD_RESET,
                "private@example.com", "Reset", "https://example.com/reset?token=raw-secret", "raw-secret", Instant.now());
        var envelope = cipher.encrypt(id, email);
        assertFalse(new String(envelope.payload(), StandardCharsets.ISO_8859_1).contains("raw-secret"));
        assertEquals(email, cipher.decrypt(id, envelope));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(UUID.randomUUID(), envelope));
        envelope.payload()[0] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(id, envelope));
    }
}

package com.sluice.api.webhook.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSecretCipherTest {
    @Test
    void roundTripsWithoutPersistingPlaintextAndRejectsTampering() {
        UUID projectId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        byte[] plaintext = "webhook-signing-secret".getBytes(StandardCharsets.UTF_8);
        WebhookSecretCipher cipher = cipher("v1", Map.of("v1", key((byte) 1)));

        WebhookSecretCipher.Envelope envelope = cipher.encrypt(projectId, endpointId, plaintext);

        assertArrayEquals(plaintext, cipher.decrypt(projectId, endpointId, envelope.encryptionVersion(),
                envelope.ciphertext(), envelope.nonce(), envelope.wrappedDataKey(), envelope.wrappingKeyId()));
        assertFalse(contains(envelope.ciphertext(), plaintext));
        assertFalse(contains(envelope.wrappedDataKey(), plaintext));

        byte[] tampered = envelope.ciphertext();
        tampered[0] ^= 1;
        assertThrows(WebhookSecretException.class, () -> cipher.decrypt(projectId, endpointId,
                envelope.encryptionVersion(), tampered, envelope.nonce(), envelope.wrappedDataKey(),
                envelope.wrappingKeyId()));
    }

    @Test
    void bindsCiphertextToProjectAndEndpointIdentity() {
        UUID projectId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        WebhookSecretCipher cipher = cipher("v1", Map.of("v1", key((byte) 2)));
        WebhookSecretCipher.Envelope envelope = cipher.encrypt(projectId, endpointId, key((byte) 9));

        assertThrows(WebhookSecretException.class, () -> cipher.decrypt(UUID.randomUUID(), endpointId,
                envelope.encryptionVersion(), envelope.ciphertext(), envelope.nonce(), envelope.wrappedDataKey(),
                envelope.wrappingKeyId()));
        assertThrows(WebhookSecretException.class, () -> cipher.decrypt(projectId, UUID.randomUUID(),
                envelope.encryptionVersion(), envelope.ciphertext(), envelope.nonce(), envelope.wrappedDataKey(),
                envelope.wrappingKeyId()));
    }

    @Test
    void rotationUsesTheNewKeyAndRetainsHistoricalDecryptability() {
        UUID projectId = UUID.randomUUID();
        UUID oldEndpoint = UUID.randomUUID();
        UUID newEndpoint = UUID.randomUUID();
        Map<String, byte[]> keyRing = Map.of("v1", key((byte) 3), "v2", key((byte) 4));
        WebhookSecretCipher beforeRotation = cipher("v1", keyRing);
        WebhookSecretCipher.Envelope oldEnvelope = beforeRotation.encrypt(projectId, oldEndpoint, key((byte) 8));

        WebhookSecretCipher afterRotation = cipher("v2", keyRing);
        WebhookSecretCipher.Envelope newEnvelope = afterRotation.encrypt(projectId, newEndpoint, key((byte) 7));

        assertEquals("v1", oldEnvelope.wrappingKeyId());
        assertEquals("v2", newEnvelope.wrappingKeyId());
        assertArrayEquals(key((byte) 8), afterRotation.decrypt(projectId, oldEndpoint,
                oldEnvelope.encryptionVersion(), oldEnvelope.ciphertext(), oldEnvelope.nonce(),
                oldEnvelope.wrappedDataKey(), oldEnvelope.wrappingKeyId()));
    }

    private static WebhookSecretCipher cipher(String currentKeyId, Map<String, byte[]> keys) {
        return new WebhookSecretCipher(new LocalDataKeyWrapper(currentKeyId, keys));
    }

    private static byte[] key(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    private static boolean contains(byte[] outer, byte[] inner) {
        if (inner.length > outer.length) return false;
        for (int i = 0; i <= outer.length - inner.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(outer, i, i + inner.length), inner)) return true;
        }
        return false;
    }
}

package com.sluice.api.webhook.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalDataKeyWrapper implements DataKeyWrapper {
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String currentKeyId;
    private final Map<String, byte[]> keys;
    private final SecureRandom random;

    public LocalDataKeyWrapper(String currentKeyId, Map<String, byte[]> keys) {
        this(currentKeyId, keys, new SecureRandom());
    }

    LocalDataKeyWrapper(String currentKeyId, Map<String, byte[]> keys, SecureRandom random) {
        if (currentKeyId == null || !keys.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("The current local wrapping key must exist in the key ring");
        }
        this.currentKeyId = currentKeyId;
        this.keys = new LinkedHashMap<>();
        keys.forEach((id, key) -> {
            if (id == null || id.isBlank() || key == null || key.length != 32) {
                throw new IllegalArgumentException("Every local wrapping key must have an ID and 32 bytes");
            }
            this.keys.put(id, Arrays.copyOf(key, key.length));
        });
        this.random = random;
    }

    public static LocalDataKeyWrapper fromConfiguration(String currentKeyId, String encodedKeyRing) {
        Map<String, byte[]> keys = new LinkedHashMap<>();
        if (encodedKeyRing != null) {
            for (String entry : encodedKeyRing.split(",")) {
                String[] parts = entry.trim().split(":", 2);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    throw new IllegalArgumentException("Local webhook key ring entries must use id:base64 format");
                }
                try {
                    if (keys.putIfAbsent(parts[0], Base64.getDecoder().decode(parts[1])) != null) {
                        throw new IllegalArgumentException("Local webhook key IDs must be unique");
                    }
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Local webhook key ring contains an invalid entry", exception);
                }
            }
        }
        return new LocalDataKeyWrapper(currentKeyId, keys);
    }

    @Override
    public WrappedDataKey wrap(byte[] dataKey) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.get(currentKeyId), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(currentKeyId));
            byte[] ciphertext = cipher.doFinal(dataKey);
            return new WrappedDataKey(currentKeyId,
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not wrap webhook data key", exception);
        }
    }

    @Override
    public byte[] unwrap(String keyId, byte[] wrappedDataKey) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new WebhookSecretException("Webhook wrapping key version is unavailable");
        }
        if (wrappedDataKey == null || wrappedDataKey.length <= NONCE_BYTES) {
            throw new WebhookSecretException("Wrapped webhook data key is incomplete");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(wrappedDataKey, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(wrappedDataKey, NONCE_BYTES, wrappedDataKey.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(keyId));
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not unwrap webhook data key", exception);
        }
    }

    private static byte[] aad(String keyId) {
        return ("sluice:webhook-data-key:" + keyId).getBytes(StandardCharsets.UTF_8);
    }
}

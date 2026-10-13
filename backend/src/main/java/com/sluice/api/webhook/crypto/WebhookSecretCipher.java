package com.sluice.api.webhook.crypto;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class WebhookSecretCipher {
    public static final short ENCRYPTION_VERSION = 1;
    private static final int DATA_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final DataKeyWrapper keyWrapper;
    private final SecureRandom random;

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookSecretCipher(DataKeyWrapper keyWrapper) {
        this(keyWrapper, new SecureRandom());
    }

    WebhookSecretCipher(DataKeyWrapper keyWrapper, SecureRandom random) {
        this.keyWrapper = keyWrapper;
        this.random = random;
    }

    public Envelope encrypt(UUID projectId, UUID endpointId, byte[] plaintextSecret) {
        requireIdentity(projectId, endpointId);
        if (plaintextSecret == null || plaintextSecret.length == 0) {
            throw new IllegalArgumentException("Webhook secret is required");
        }

        byte[] dataKey = new byte[DATA_KEY_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(dataKey);
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(projectId, endpointId, ENCRYPTION_VERSION));
            byte[] ciphertext = cipher.doFinal(plaintextSecret);
            DataKeyWrapper.WrappedDataKey wrapped = keyWrapper.wrap(dataKey);
            return new Envelope(ENCRYPTION_VERSION, ciphertext, nonce, wrapped.ciphertext(), wrapped.keyId());
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not protect webhook signing secret", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    public byte[] decrypt(UUID projectId, UUID endpointId, short encryptionVersion, byte[] ciphertext,
                          byte[] nonce, byte[] wrappedDataKey, String wrappingKeyId) {
        requireIdentity(projectId, endpointId);
        if (encryptionVersion != ENCRYPTION_VERSION) {
            throw new WebhookSecretException("Unsupported webhook secret encryption version");
        }
        if (ciphertext == null || nonce == null || nonce.length != NONCE_BYTES
                || wrappedDataKey == null || wrappingKeyId == null || wrappingKeyId.isBlank()) {
            throw new WebhookSecretException("Webhook secret envelope is incomplete");
        }

        byte[] dataKey = null;
        try {
            dataKey = keyWrapper.unwrap(wrappingKeyId, wrappedDataKey);
            if (dataKey.length != DATA_KEY_BYTES) {
                throw new WebhookSecretException("Unwrapped webhook data key has an invalid length");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(projectId, endpointId, encryptionVersion));
            return cipher.doFinal(ciphertext);
        } catch (WebhookSecretException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not open webhook signing secret", exception);
        } finally {
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
        }
    }

    private static byte[] aad(UUID projectId, UUID endpointId, short version) {
        return ("sluice:webhook-secret:v" + version + ":" + projectId + ":" + endpointId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void requireIdentity(UUID projectId, UUID endpointId) {
        if (projectId == null || endpointId == null) {
            throw new IllegalArgumentException("Project and endpoint identifiers are required");
        }
    }

    public record Envelope(short encryptionVersion, byte[] ciphertext, byte[] nonce,
                           byte[] wrappedDataKey, String wrappingKeyId) {
        public Envelope {
            ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            nonce = Arrays.copyOf(nonce, nonce.length);
            wrappedDataKey = Arrays.copyOf(wrappedDataKey, wrappedDataKey.length);
        }

        @Override public byte[] ciphertext() { return Arrays.copyOf(ciphertext, ciphertext.length); }
        @Override public byte[] nonce() { return Arrays.copyOf(nonce, nonce.length); }
        @Override public byte[] wrappedDataKey() { return Arrays.copyOf(wrappedDataKey, wrappedDataKey.length); }
    }
}

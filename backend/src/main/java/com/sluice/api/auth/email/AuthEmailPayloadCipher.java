package com.sluice.api.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.webhook.crypto.DataKeyWrapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Envelope encryption with an email-specific, row-bound authenticated context. */
@Component
@ConditionalOnApiRuntime
public class AuthEmailPayloadCipher {
    private final DataKeyWrapper wrapper;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public AuthEmailPayloadCipher(DataKeyWrapper wrapper, ObjectMapper mapper) {
        this.wrapper = wrapper;
        this.mapper = mapper;
    }

    public Envelope encrypt(UUID id, AuthEmailProvider.AuthEmail email) {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        random.nextBytes(key);
        random.nextBytes(nonce);
        byte[] plaintext = null;
        try {
            plaintext = mapper.writeValueAsBytes(new StoredEmail(email.kind(), email.recipient(), email.subject(),
                    email.text(), email.token(), email.createdAt().toString()));
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, id, key, nonce);
            byte[] payload = cipher.doFinal(plaintext);
            DataKeyWrapper.WrappedDataKey wrapped = wrapper.wrap(key);
            return new Envelope(payload, nonce, wrapped.ciphertext(), wrapped.keyId());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt authentication email");
        } finally {
            Arrays.fill(key, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    public AuthEmailProvider.AuthEmail decrypt(UUID id, Envelope envelope) {
        byte[] key = null;
        byte[] plaintext = null;
        try {
            key = wrapper.unwrap(envelope.keyId(), envelope.wrappedKey());
            if (key.length != 32) throw new IllegalStateException("Invalid data key");
            plaintext = cipher(Cipher.DECRYPT_MODE, id, key, envelope.nonce()).doFinal(envelope.payload());
            StoredEmail stored = mapper.readValue(plaintext, StoredEmail.class);
            return new AuthEmailProvider.AuthEmail(stored.kind(), stored.recipient(), stored.subject(), stored.text(),
                    stored.token(), Instant.parse(stored.createdAt()));
        } catch (Exception exception) {
            // Provider/Jackson exceptions can include message content. Never retain them in logs.
            throw new IllegalStateException("Could not decrypt authentication email");
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private Cipher cipher(int mode, UUID id, byte[] key, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("sluice:auth-email:v1:" + id).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    public record Envelope(byte[] payload, byte[] nonce, byte[] wrappedKey, String keyId) {}

    // Keep persistence portable without changing the application's shared JSON mapper.
    private record StoredEmail(AuthEmailProvider.Kind kind, String recipient, String subject, String text,
                               String token, String createdAt) {}
}

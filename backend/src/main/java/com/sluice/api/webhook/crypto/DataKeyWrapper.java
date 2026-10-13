package com.sluice.api.webhook.crypto;

import java.util.Arrays;

public interface DataKeyWrapper {
    WrappedDataKey wrap(byte[] dataKey);

    byte[] unwrap(String keyId, byte[] wrappedDataKey);

    record WrappedDataKey(String keyId, byte[] ciphertext) {
        public WrappedDataKey {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalArgumentException("keyId is required");
            }
            if (ciphertext == null || ciphertext.length == 0) {
                throw new IllegalArgumentException("wrapped key ciphertext is required");
            }
            ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        }

        @Override
        public byte[] ciphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }
    }
}

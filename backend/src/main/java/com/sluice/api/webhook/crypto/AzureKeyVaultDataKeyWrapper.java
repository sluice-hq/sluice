package com.sluice.api.webhook.crypto;

import com.azure.core.credential.TokenCredential;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AzureKeyVaultDataKeyWrapper implements DataKeyWrapper {
    private final KeyClient keyClient;
    private final String keyName;
    private final Function<String, CryptographyClient> cryptographyClientFactory;
    private final Map<String, CryptographyClient> clients = new ConcurrentHashMap<>();

    public AzureKeyVaultDataKeyWrapper(KeyClient keyClient, TokenCredential credential, String keyName) {
        this(keyClient, keyName, keyId -> new CryptographyClientBuilder()
                .keyIdentifier(keyId)
                .credential(credential)
                .buildClient());
    }

    AzureKeyVaultDataKeyWrapper(KeyClient keyClient, String keyName,
                                Function<String, CryptographyClient> cryptographyClientFactory) {
        this.keyClient = keyClient;
        this.keyName = keyName;
        this.cryptographyClientFactory = cryptographyClientFactory;
    }

    @Override
    public WrappedDataKey wrap(byte[] dataKey) {
        try {
            KeyVaultKey current = keyClient.getKey(keyName);
            String versionedKeyId = current.getId();
            byte[] ciphertext = client(versionedKeyId)
                    .wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, dataKey)
                    .getEncryptedKey();
            return new WrappedDataKey(versionedKeyId, ciphertext);
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not wrap webhook data key with Azure Key Vault", exception);
        }
    }

    @Override
    public byte[] unwrap(String keyId, byte[] wrappedDataKey) {
        try {
            return client(keyId).unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedDataKey).getKey();
        } catch (Exception exception) {
            throw new WebhookSecretException("Could not unwrap webhook data key with Azure Key Vault", exception);
        }
    }

    private CryptographyClient client(String keyId) {
        return clients.computeIfAbsent(keyId, cryptographyClientFactory);
    }
}

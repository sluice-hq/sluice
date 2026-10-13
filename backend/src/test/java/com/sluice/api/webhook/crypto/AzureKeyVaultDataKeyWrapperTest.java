package com.sluice.api.webhook.crypto;

import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.cryptography.models.UnwrapResult;
import com.azure.security.keyvault.keys.cryptography.models.WrapResult;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AzureKeyVaultDataKeyWrapperTest {
    @Test
    void storesTheCurrentVersionAndUsesTheStoredVersionForHistoricalUnwrap() {
        String currentId = "https://vault.test/keys/webhook-secret-kek/v2";
        String historicalId = "https://vault.test/keys/webhook-secret-kek/v1";
        byte[] dataKey = new byte[32];
        byte[] wrapped = new byte[]{1, 2, 3};
        KeyClient keyClient = mock(KeyClient.class);
        KeyVaultKey currentKey = mock(KeyVaultKey.class);
        CryptographyClient currentClient = mock(CryptographyClient.class);
        CryptographyClient historicalClient = mock(CryptographyClient.class);
        WrapResult wrapResult = mock(WrapResult.class);
        UnwrapResult unwrapResult = mock(UnwrapResult.class);
        when(keyClient.getKey("webhook-secret-kek")).thenReturn(currentKey);
        when(currentKey.getId()).thenReturn(currentId);
        when(currentClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, dataKey)).thenReturn(wrapResult);
        when(wrapResult.getEncryptedKey()).thenReturn(wrapped);
        when(historicalClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrapped)).thenReturn(unwrapResult);
        when(unwrapResult.getKey()).thenReturn(dataKey);
        AzureKeyVaultDataKeyWrapper wrapper = new AzureKeyVaultDataKeyWrapper(keyClient,
                "webhook-secret-kek", Map.of(currentId, currentClient, historicalId, historicalClient)::get);

        DataKeyWrapper.WrappedDataKey result = wrapper.wrap(dataKey);

        assertEquals(currentId, result.keyId());
        assertArrayEquals(wrapped, result.ciphertext());
        assertArrayEquals(dataKey, wrapper.unwrap(historicalId, wrapped));
        verify(historicalClient).unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrapped);
    }
}

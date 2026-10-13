package com.sluice.api.webhook.crypto;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnApiRuntime
public class WebhookSecretEncryptionConfiguration {
    @Bean
    @ConditionalOnProperty(name = "sluice.webhooks.encryption.provider", havingValue = "local", matchIfMissing = true)
    DataKeyWrapper localDataKeyWrapper(
            @Value("${sluice.webhooks.encryption.local.current-key-id:v1}") String currentKeyId,
            @Value("${sluice.webhooks.encryption.local.keys}") String encodedKeyRing) {
        return LocalDataKeyWrapper.fromConfiguration(currentKeyId, encodedKeyRing);
    }

    @Bean
    @ConditionalOnProperty(name = "sluice.webhooks.encryption.provider", havingValue = "azure")
    DataKeyWrapper azureDataKeyWrapper(
            @Value("${sluice.webhooks.encryption.azure.vault-url}") String vaultUrl,
            @Value("${sluice.webhooks.encryption.azure.key-name}") String keyName,
            @Value("${sluice.webhooks.encryption.azure.managed-identity-client-id:}") String clientId) {
        DefaultAzureCredentialBuilder credentialBuilder = new DefaultAzureCredentialBuilder();
        if (clientId != null && !clientId.isBlank()) {
            credentialBuilder.managedIdentityClientId(clientId);
        }
        TokenCredential credential = credentialBuilder.build();
        KeyClient keyClient = new KeyClientBuilder().vaultUrl(vaultUrl).credential(credential).buildClient();
        return new AzureKeyVaultDataKeyWrapper(keyClient, credential, keyName);
    }
}

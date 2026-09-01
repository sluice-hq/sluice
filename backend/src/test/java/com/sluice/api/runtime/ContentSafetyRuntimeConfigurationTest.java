package com.sluice.api.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.governance.AzureContentSafetyProvider;
import com.sluice.api.pipeline.processor.ContentSafetyProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafetyRuntimeConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeModeConfiguration.class, AzureContentSafetyProvider.class,
                    ContentSafetyProcessor.class)
            .withBean(ObjectMapper.class)
            .withPropertyValues("sluice.governance.provider=azure");

    @Test
    void apiRuntimeKeepsProcessorMetadataWithoutAzureExecutionCredentials() {
        contextRunner.withPropertyValues("sluice.runtime.mode=api").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AzureContentSafetyProvider.class);
            assertThat(context).hasSingleBean(ContentSafetyProcessor.class);
            assertThat(context.getBean(ContentSafetyProcessor.class).getManifest().slug())
                    .isEqualTo("governance.content-safety");
        });
    }

    @Test
    void workerRuntimeFailsFastWithoutAzureExecutionCredentials() {
        contextRunner.withPropertyValues("sluice.runtime.mode=worker").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("Azure Content Safety endpoint and API key are required");
        });
    }

    @Test
    void workerRuntimeCreatesAzureExecutionProviderWhenCredentialsExist() {
        contextRunner.withPropertyValues(
                "sluice.runtime.mode=worker",
                "sluice.governance.azure.endpoint=https://content-safety.example.test",
                "sluice.governance.azure.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AzureContentSafetyProvider.class);
                    assertThat(context).hasSingleBean(ContentSafetyProcessor.class);
                });
    }
}

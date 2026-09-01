package com.sluice.api.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeModeConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeModeConfiguration.class, ApiMarkerConfig.class, WorkerMarkerConfig.class);

    @Test
    void defaultRuntimeEnablesApiAndWorkerCapabilities() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("apiMarker");
            assertThat(context).hasBean("workerMarker");
        });
    }

    @Test
    void apiRuntimeDisablesWorkerCapabilities() {
        contextRunner.withPropertyValues("sluice.runtime.mode=api").run(context -> {
            assertThat(context).hasBean("apiMarker");
            assertThat(context).doesNotHaveBean("workerMarker");
        });
    }

    @Test
    void workerRuntimeDisablesApiCapabilities() {
        contextRunner.withPropertyValues("sluice.runtime.mode=worker").run(context -> {
            assertThat(context).doesNotHaveBean("apiMarker");
            assertThat(context).hasBean("workerMarker");
        });
    }

    @Test
    void invalidRuntimeModeFailsConfigurationBinding() {
        contextRunner.withPropertyValues("sluice.runtime.mode=invalid").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("prefix=sluice.runtime");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnApiRuntime
    static class ApiMarkerConfig {
        @Bean
        String apiMarker() {
            return "api";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWorkerRuntime
    static class WorkerMarkerConfig {
        @Bean
        Integer workerMarker() {
            return 1;
        }
    }
}

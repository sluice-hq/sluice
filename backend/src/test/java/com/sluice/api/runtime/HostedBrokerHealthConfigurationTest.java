package com.sluice.api.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.health.RabbitHealthContributorAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class HostedBrokerHealthConfigurationTest {
    @Test
    void productionDoesNotProbeOrStartTheUnusedLocalRabbitAdapter() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class,
                        RabbitHealthContributorAutoConfiguration.class))
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource(
                                new ClassPathResource("application-production.properties")));
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("rabbitHealthContributor");
                    assertThat(context.getEnvironment().getProperty("spring.rabbitmq.listener.simple.auto-startup"))
                            .isEqualTo("false");
                });
    }
}

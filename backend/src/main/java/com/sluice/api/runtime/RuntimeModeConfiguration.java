package com.sluice.api.runtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SluiceRuntimeProperties.class)
public class RuntimeModeConfiguration {
}

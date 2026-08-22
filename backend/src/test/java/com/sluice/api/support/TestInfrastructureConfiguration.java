package com.sluice.api.support;

import com.sluice.api.storage.StorageService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructureConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("sluice_test")
                .withUsername("sluice_test")
                .withPassword("sluice_test");
    }

    @Bean
    StorageService storageService() {
        return Mockito.mock(StorageService.class);
    }
}

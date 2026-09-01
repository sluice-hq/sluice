package com.sluice.api.runtime;

import com.sluice.api.auth.controller.AuthController;
import com.sluice.api.security.JwtService;
import com.sluice.api.support.TestInfrastructureConfiguration;
import com.sluice.api.worker.JobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "sluice.runtime.mode=worker")
@ActiveProfiles("test")
@Import(TestInfrastructureConfiguration.class)
class WorkerRuntimeApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void workerRuntimeLoadsQueueWorkerWithoutHttpOrAuthenticationBeans() {
        assertThat(applicationContext.getBeansOfType(JobWorker.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AuthController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JwtService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
    }
}

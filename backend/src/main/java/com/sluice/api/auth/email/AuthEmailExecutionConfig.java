package com.sluice.api.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AuthEmailExecutionConfig {
    public static final String EXECUTOR = "authEmailTaskExecutor";

    @Bean(name = EXECUTOR)
    ThreadPoolTaskExecutor authEmailTaskExecutor(
            @Value("${sluice.auth.email.executor.threads:2}") int threads,
            @Value("${sluice.auth.email.executor.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int boundedThreads = Math.max(1, Math.min(threads, 16));
        executor.setCorePoolSize(boundedThreads);
        executor.setMaxPoolSize(boundedThreads);
        executor.setQueueCapacity(Math.max(10, Math.min(queueCapacity, 10_000)));
        executor.setThreadNamePrefix("sluice-auth-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

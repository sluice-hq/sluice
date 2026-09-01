package com.sluice.api.observability;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnApiRuntime
public class ObservabilitySchedulingConfig {

    @Bean(name = "dependencyHealthTaskScheduler")
    ThreadPoolTaskScheduler dependencyHealthTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("dependency-health-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}

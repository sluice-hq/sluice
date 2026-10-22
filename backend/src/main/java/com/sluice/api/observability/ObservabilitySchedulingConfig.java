package com.sluice.api.observability;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnApiRuntime
public class ObservabilitySchedulingConfig {

    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("sluice-timers-");
    }

    @Bean(name = "authEmailRequestTaskScheduler")
    ThreadPoolTaskScheduler authEmailRequestTaskScheduler() {
        return scheduler("auth-email-prepare-");
    }

    @Bean(name = "authEmailDeliveryTaskScheduler")
    ThreadPoolTaskScheduler authEmailDeliveryTaskScheduler() {
        return scheduler("auth-email-delivery-");
    }

    @Bean(name = "dependencyHealthTaskScheduler")
    ThreadPoolTaskScheduler dependencyHealthTaskScheduler() {
        return scheduler("dependency-health-");
    }

    private ThreadPoolTaskScheduler scheduler(String threadPrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadPrefix);
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}

package com.sluice.api.observability;

import com.sluice.api.auth.email.AuthEmailOutboxDispatcher;
import com.sluice.api.auth.email.AuthEmailRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilitySchedulingConfigTest {
    @Test
    void blockedEmailPreparationAndDeliveryDoNotBlockRunTimersOrHealth() {
        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilitySchedulingConfig.class, ProbeConfig.class)
                .withPropertyValues("sluice.runtime.mode=api")
                .run(context -> {
                    Routes routes = context.getBean(Routes.class);
                    try {
                        assertTrue(routes.requestStarted.await(5, TimeUnit.SECONDS));
                        assertTrue(routes.deliveryStarted.await(5, TimeUnit.SECONDS));
                        assertTrue(routes.normalProgress.await(5, TimeUnit.SECONDS));
                        assertTrue(routes.healthProgress.await(5, TimeUnit.SECONDS));
                        assertTrue(routes.normalThread.get().startsWith("sluice-timers-"));
                        assertTrue(routes.healthThread.get().startsWith("dependency-health-"));
                        assertEquals("authEmailRequestTaskScheduler", AuthEmailRequestProcessor.class
                                .getMethod("process").getAnnotation(Scheduled.class).scheduler());
                        assertEquals("authEmailDeliveryTaskScheduler", AuthEmailOutboxDispatcher.class
                                .getMethod("dispatch").getAnnotation(Scheduled.class).scheduler());
                        assertEquals("authEmailDeliveryTaskScheduler", AuthEmailOutboxDispatcher.class
                                .getMethod("cleanup").getAnnotation(Scheduled.class).scheduler());
                    } finally {
                        routes.release.countDown();
                    }
                });
    }

    @Test
    void workerDoesNotCreateApiOrEmailSchedulers() {
        new ApplicationContextRunner().withUserConfiguration(ObservabilitySchedulingConfig.class)
                .withPropertyValues("sluice.runtime.mode=worker")
                .run(context -> assertTrue(context.getBeansOfType(TaskScheduler.class).isEmpty()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class ProbeConfig {
        @Bean Routes routes() { return new Routes(); }
    }

    static class Routes {
        final CountDownLatch requestStarted = new CountDownLatch(1);
        final CountDownLatch deliveryStarted = new CountDownLatch(1);
        final CountDownLatch normalProgress = new CountDownLatch(1);
        final CountDownLatch healthProgress = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<String> normalThread = new AtomicReference<>();
        final AtomicReference<String> healthThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 25, scheduler = "authEmailRequestTaskScheduler")
        void prepare() { block(requestStarted); }

        @Scheduled(fixedDelay = 25, scheduler = "authEmailDeliveryTaskScheduler")
        void deliver() { block(deliveryStarted); }

        @Scheduled(fixedDelay = 25)
        void normalTimer() {
            if (bothBlocked()) {
                normalThread.set(Thread.currentThread().getName());
                normalProgress.countDown();
            }
        }

        @Scheduled(fixedDelay = 25, scheduler = "dependencyHealthTaskScheduler")
        void health() {
            if (bothBlocked()) {
                healthThread.set(Thread.currentThread().getName());
                healthProgress.countDown();
            }
        }

        private boolean bothBlocked() {
            return requestStarted.getCount() == 0 && deliveryStarted.getCount() == 0 && release.getCount() > 0;
        }

        private void block(CountDownLatch started) {
            started.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

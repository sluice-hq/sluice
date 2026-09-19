package com.sluice.api.messaging;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.sluice.api.runtime.ConditionalOnWorkerRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Starts consumption only after the Spring context is ready and closes it during graceful shutdown. */
@Component
@ConditionalOnWorkerRuntime
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "servicebus")
public class AzureServiceBusProcessorLifecycle implements SmartLifecycle {
    private final ServiceBusProcessorClient processor;
    private volatile boolean running;

    public AzureServiceBusProcessorLifecycle(ServiceBusProcessorClient processor) {
        this.processor = processor;
    }

    @Override
    public void start() {
        processor.start();
        running = true;
    }

    @Override
    public void stop() {
        try {
            processor.stop();
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

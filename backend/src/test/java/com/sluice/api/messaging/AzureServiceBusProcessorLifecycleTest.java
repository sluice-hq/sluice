package com.sluice.api.messaging;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AzureServiceBusProcessorLifecycleTest {

    @Test
    void startsAndStopsConsumptionWithoutOwningFinalClientDestruction() {
        ServiceBusProcessorClient processor = mock(ServiceBusProcessorClient.class);
        AzureServiceBusProcessorLifecycle lifecycle = new AzureServiceBusProcessorLifecycle(processor);

        lifecycle.start();

        assertTrue(lifecycle.isRunning());
        verify(processor).start();

        lifecycle.stop();

        assertFalse(lifecycle.isRunning());
        verify(processor).stop();
        verify(processor, never()).close();
    }
}

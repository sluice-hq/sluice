package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;

import java.util.UUID;

public interface RunQueuePublisher {
    void publish(UUID deliveryId, JobMessage message);
}

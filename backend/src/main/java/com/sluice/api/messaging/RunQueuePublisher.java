package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;

public interface RunQueuePublisher {
    void publish(JobMessage message);
}

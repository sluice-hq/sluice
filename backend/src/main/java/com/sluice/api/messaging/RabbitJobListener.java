package com.sluice.api.messaging;

import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.runtime.ConditionalOnWorkerRuntime;
import com.sluice.api.worker.JobWorker;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local RabbitMQ delivery adapter. The worker remains independent of the broker. */
@Component
@ConditionalOnWorkerRuntime
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "rabbit", matchIfMissing = true)
public class RabbitJobListener {
    private final JobWorker worker;

    public RabbitJobListener(JobWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void receive(JobMessage message) throws Exception {
        worker.processJob(message);
    }
}

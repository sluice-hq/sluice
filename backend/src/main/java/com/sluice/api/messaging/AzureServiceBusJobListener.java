package com.sluice.api.messaging;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sluice.api.job.service.JobService;
import com.sluice.api.messaging.dto.JobMessage;
import com.sluice.api.observability.SluiceMetrics;
import com.sluice.api.runtime.ConditionalOnWorkerRuntime;
import com.sluice.api.worker.JobWorker;
import com.sluice.api.worker.PermanentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWorkerRuntime
@ConditionalOnProperty(name = "sluice.messaging.provider", havingValue = "servicebus")
public class AzureServiceBusJobListener {
    private static final Logger log = LoggerFactory.getLogger(AzureServiceBusJobListener.class);

    private final JobWorker worker;
    private final JobService jobs;
    private final ObjectMapper objectMapper;
    private final SluiceMetrics metrics;
    private final int maxDeliveryCount;

    public AzureServiceBusJobListener(JobWorker worker, JobService jobs, ObjectMapper objectMapper,
                                      SluiceMetrics metrics,
                                      @Value("${sluice.messaging.service-bus.max-delivery-count:5}")
                                      int maxDeliveryCount) {
        this.worker = worker;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.maxDeliveryCount = Math.max(1, maxDeliveryCount);
    }

    public void receive(ServiceBusReceivedMessageContext context) {
        JobMessage message = null;
        try {
            message = objectMapper.readValue(context.getMessage().getBody().toString(), JobMessage.class);
            worker.processJob(message);
        } catch (Exception exception) {
            settleProcessingFailure(context, message, exception);
            return;
        }

        try {
            context.complete();
            metrics.queueConsume("completed");
        } catch (RuntimeException exception) {
            metrics.queueConsume("settlement_failed");
            log.warn("service_bus_message_completion_failed jobId={} deliveryCount={} errorType={}",
                    message.getJobId(), context.getMessage().getDeliveryCount(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void settleProcessingFailure(ServiceBusReceivedMessageContext context, JobMessage message,
                                         Exception exception) {
        long deliveryCount = context.getMessage().getDeliveryCount();
        boolean permanent = exception instanceof PermanentProcessingException
                || exception instanceof IllegalArgumentException
                || exception instanceof JsonProcessingException;
        if (permanent || deliveryCount >= maxDeliveryCount) {
            failRunSafely(message, permanent ? "broker_message_invalid" : "broker_retry_exhausted");
            context.deadLetter(new DeadLetterOptions()
                    .setDeadLetterReason(permanent ? "PermanentProcessingFailure" : "MaxDeliveryCountReached")
                    .setDeadLetterErrorDescription("Sluice could not process this run message safely"));
            metrics.queueConsume("dead_lettered");
            log.error("service_bus_message_dead_lettered jobId={} deliveryCount={} errorType={}",
                    message == null ? null : message.getJobId(), deliveryCount,
                    exception.getClass().getSimpleName());
        } else {
            context.abandon();
            metrics.queueConsume("abandoned");
            log.warn("service_bus_message_abandoned jobId={} deliveryCount={} errorType={}",
                    message == null ? null : message.getJobId(), deliveryCount,
                    exception.getClass().getSimpleName());
        }
    }

    public void onError(ServiceBusErrorContext context) {
        metrics.queueConsume("receiver_error");
        log.error("service_bus_receiver_error namespace={} entity={} source={}",
                context.getFullyQualifiedNamespace(), context.getEntityPath(), context.getErrorSource(),
                context.getException());
    }

    private void failRunSafely(JobMessage message, String code) {
        if (message == null || message.getJobId() == null) return;
        try {
            jobs.failJobSystem(message.getJobId(), code, "Queue delivery could not be completed");
        } catch (Exception exception) {
            log.warn("service_bus_run_failure_record_skipped jobId={} reason={}",
                    message.getJobId(), exception.getClass().getSimpleName());
        }
    }
}

package com.sluice.api.job.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class JobStatusChangedEvent extends ApplicationEvent {
    
    private final UUID jobId;
    private final com.sluice.api.job.domain.JobStatus status;

    public JobStatusChangedEvent(Object source, UUID jobId, com.sluice.api.job.domain.JobStatus status) {
        super(source);
        this.jobId = jobId;
        this.status = status;
    }

    public UUID getJobId() {
        return jobId;
    }

    public com.sluice.api.job.domain.JobStatus getStatus() {
        return status;
    }
}

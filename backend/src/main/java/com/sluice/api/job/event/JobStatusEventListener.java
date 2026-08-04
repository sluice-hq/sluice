package com.sluice.api.job.event;

import com.sluice.api.job.service.JobEventService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class JobStatusEventListener {

    private final JobEventService jobEventService;

    public JobStatusEventListener(JobEventService jobEventService) {
        this.jobEventService = jobEventService;
    }

    @EventListener
    public void handleJobStatusChanged(JobStatusChangedEvent event) {
        jobEventService.broadcastJobStatusChange(event.getJobId(), event.getStatus());
    }
}

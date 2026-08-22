package com.sluice.api.job.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.service.JobEventService;
import com.sluice.api.job.service.JobService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobControllerTest {

    @Test
    void rejectsEventSubscriptionWhenJobIsOutsideProject() {
        UUID jobId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(UUID.randomUUID(), null, true);
        JobService jobs = mock(JobService.class);
        JobEventService events = mock(JobEventService.class);
        when(jobs.getJob(jobId, context)).thenReturn(Optional.empty());

        var response = new JobController(jobs, events).subscribeToJobEvents(jobId, context);

        assertEquals(404, response.getStatusCode().value());
        verifyNoInteractions(events);
    }

    @Test
    void subscribesWhenJobBelongsToProject() {
        UUID jobId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(UUID.randomUUID(), null, true);
        JobService jobs = mock(JobService.class);
        JobEventService events = mock(JobEventService.class);
        Job job = mock(Job.class);
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        when(jobs.getJob(jobId, context)).thenReturn(Optional.of(job));
        when(events.subscribeToJobEvents(jobId)).thenReturn(emitter);

        var response = new JobController(jobs, events).subscribeToJobEvents(jobId, context);

        assertEquals(200, response.getStatusCode().value());
        assertSame(emitter, response.getBody());
    }
}

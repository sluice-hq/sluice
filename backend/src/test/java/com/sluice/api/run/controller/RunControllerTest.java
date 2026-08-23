package com.sluice.api.run.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.service.JobEventService;
import com.sluice.api.run.dto.CreateRunRequest;
import com.sluice.api.run.dto.RunResponse;
import com.sluice.api.run.service.RunService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RunControllerTest {
    @Test
    void createsRunWithAcceptedResponseAndStablePublicShape() {
        UUID projectId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        ProjectContext context = new ProjectContext(projectId, null, true);
        Job job = new Job(runId, assetId, JobStatus.QUEUED, now, now, projectId);
        RunResponse response = new RunResponse(runId, "QUEUED",
                new RunResponse.PipelineReference("product-images", 3), assetId, List.of(), List.of(), now, now);
        RunService runs = mock(RunService.class);
        when(runs.create(any(), eq("request-1"), eq(context))).thenReturn(job);
        when(runs.get(runId, context)).thenReturn(Optional.of(response));

        var result = new RunController(runs, mock(JobEventService.class)).create(
                new CreateRunRequest("product-images", "stable", null, assetId), "request-1", context);

        assertEquals(202, result.getStatusCode().value());
        assertEquals(runId, result.getBody().id());
        assertEquals(3, result.getBody().pipeline().version());
    }

    @Test
    void rejectsEventsForRunsOutsideTheProject() {
        UUID runId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(UUID.randomUUID(), null, true);
        RunService runs = mock(RunService.class);
        when(runs.get(runId, context)).thenReturn(Optional.empty());

        var result = new RunController(runs, mock(JobEventService.class)).events(runId, context);

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void subscribesToProjectScopedRunEvents() {
        UUID runId = UUID.randomUUID();
        ProjectContext context = new ProjectContext(UUID.randomUUID(), null, true);
        RunService runs = mock(RunService.class);
        JobEventService events = mock(JobEventService.class);
        when(runs.get(runId, context)).thenReturn(Optional.of(mock(RunResponse.class)));
        SseEmitter emitter = new SseEmitter();
        when(events.subscribeToJobEvents(runId)).thenReturn(emitter);

        var result = new RunController(runs, events).events(runId, context);

        assertEquals(200, result.getStatusCode().value());
        verify(events).subscribeToJobEvents(runId);
    }
}

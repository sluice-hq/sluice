package com.sluice.api.job.controller;

import com.sluice.api.job.dto.JobResponse;
import com.sluice.api.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final com.sluice.api.job.service.JobEventService jobEventService;

    public JobController(JobService jobService, com.sluice.api.job.service.JobEventService jobEventService) {
        this.jobService = jobService;
        this.jobEventService = jobEventService;
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(Pageable pageable) {
        Page<JobResponse> jobs = jobService.getJobs(pageable).map(job -> new JobResponse(
                job.getId(),
                job.getAssetId(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        ));
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobStatus(@PathVariable UUID id) {
        return jobService.getJob(id)
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getAssetId(),
                        job.getStatus().name(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/events", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeToJobEvents(@PathVariable UUID id) {
        return jobEventService.subscribeToJobEvents(id);
    }
}

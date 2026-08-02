package com.sluice.api.job.controller;

import com.sluice.api.job.dto.JobResponse;
import com.sluice.api.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
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
}

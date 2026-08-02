package com.sluice.api.job.service;

import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job createJob(UUID assetId) {
        Instant now = Instant.now();
        Job job = new Job(UUID.randomUUID(), assetId, JobStatus.QUEUED, now, now);
        return jobRepository.save(job);
    }

    public Optional<Job> getJob(UUID id) {
        return jobRepository.findById(id);
    }

    public Job updateJobStatus(UUID id, JobStatus newStatus) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found: " + id));
        job.setStatus(newStatus);
        return jobRepository.save(job);
    }
}

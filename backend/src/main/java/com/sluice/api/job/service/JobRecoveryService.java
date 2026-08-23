package com.sluice.api.job.service;

import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

@Service
@Profile("!test")
public class JobRecoveryService {

    private final JobRepository jobRepository;
    private final JobService jobService;

    public JobRecoveryService(JobRepository jobRepository, JobService jobService) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "${sluice.recovery.zombie.rate:60000}")
    public void recoverZombies() {
        // 1. Zombie Scan: Reset jobs stuck in RUNNING for > 1h to QUEUED
        Instant threshold = Instant.now().minus(Duration.ofHours(1));
        List<Job> zombies = jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.RUNNING, threshold);
        
        for (Job job : zombies) {
            try {
                jobService.scheduleRetry(job.getId(), "worker_interrupted", "Worker execution was interrupted",
                        Duration.ZERO);
                System.out.println("Recovered zombie job " + job.getId() + " to RETRY_WAIT status.");
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                // Ignore, another instance handled it or the worker just finished
            } catch (Exception e) {
                System.err.println("Failed to recover zombie job " + job.getId() + ": " + e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${sluice.recovery.orphan.rate:60000}")
    public void recoverOrphans() {
        jobService.requeueDueRetries(25);
    }
}

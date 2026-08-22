package com.sluice.api.job.service;

import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.messaging.RabbitMqConfig;
import com.sluice.api.messaging.dto.JobMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;

    public JobRecoveryService(JobRepository jobRepository, JobService jobService, RabbitTemplate rabbitTemplate) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${sluice.recovery.zombie.rate:60000}")
    public void recoverZombies() {
        // 1. Zombie Scan: Reset jobs stuck in RUNNING for > 1h to QUEUED
        Instant threshold = Instant.now().minus(Duration.ofHours(1));
        List<Job> zombies = jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.RUNNING, threshold);
        
        for (Job job : zombies) {
            try {
                jobService.updateJobStatusSystem(job.getId(), JobStatus.QUEUED);
                System.out.println("Recovered zombie job " + job.getId() + " to QUEUED status.");
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                // Ignore, another instance handled it or the worker just finished
            } catch (Exception e) {
                System.err.println("Failed to recover zombie job " + job.getId() + ": " + e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${sluice.recovery.orphan.rate:60000}")
    public void recoverOrphans() {
        // 2. Orphan Scan: Republish jobs stuck in QUEUED for > 5m to RabbitMQ
        Instant threshold = Instant.now().minus(Duration.ofMinutes(5));
        List<Job> orphans = jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.QUEUED, threshold);
        
        for (Job job : orphans) {
            try {
                // Note: We do not modify updated_at to suppress duplicates, duplicate messages are safe
                rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.ROUTING_KEY, new JobMessage(job.getId(), job.getAssetId()));
                System.out.println("Republished orphan job " + job.getId() + " to RabbitMQ.");
            } catch (Exception e) {
                System.err.println("Failed to republish orphan job " + job.getId() + ": " + e.getMessage());
            }
        }
    }
}

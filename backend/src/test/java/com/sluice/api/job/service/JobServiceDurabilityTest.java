package com.sluice.api.job.service;

import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.domain.RunAttempt;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.job.repository.RunAttemptRepository;
import com.sluice.api.outbox.service.OutboxService;
import com.sluice.api.pipeline.service.PipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceDurabilityTest {
    @Test
    void onlyTheAtomicQueuedClaimCreatesAnAttempt() {
        UUID id = UUID.randomUUID();
        JobRepository jobs = mock(JobRepository.class);
        RunAttemptRepository attempts = mock(RunAttemptRepository.class);
        when(jobs.claimQueuedJob(eq(id), any(), eq(JobStatus.RUNNING))).thenReturn(0);
        JobService service = service(jobs, attempts);

        assertTrue(service.claimQueuedJob(id).isEmpty());
        verifyNoInteractions(attempts);
    }

    @Test
    void aSuccessfulClaimPersistsTheAttemptNumber() {
        UUID id = UUID.randomUUID();
        Job running = new Job(id, UUID.randomUUID(), JobStatus.RUNNING,
                Instant.now(), Instant.now(), UUID.randomUUID());
        running.setRetryCount(1);
        JobRepository jobs = mock(JobRepository.class);
        RunAttemptRepository attempts = mock(RunAttemptRepository.class);
        when(jobs.claimQueuedJob(eq(id), any(), eq(JobStatus.RUNNING))).thenReturn(1);
        when(jobs.findById(id)).thenReturn(Optional.of(running));

        assertTrue(service(jobs, attempts).claimQueuedJob(id).isPresent());
        verify(attempts).save(argThat(attempt -> attempt.getAttemptNumber() == 2
                && attempt.getStatus().equals("RUNNING")));
    }

    @Test
    void aTerminalRunCannotTransitionBackToRunning() {
        UUID id = UUID.randomUUID();
        Job completed = new Job(id, UUID.randomUUID(), JobStatus.COMPLETED,
                Instant.now(), Instant.now(), UUID.randomUUID());
        JobRepository jobs = mock(JobRepository.class);
        when(jobs.findById(id)).thenReturn(Optional.of(completed));

        assertThrows(IllegalStateException.class,
                () -> service(jobs, mock(RunAttemptRepository.class)).updateJobStatusSystem(id, JobStatus.RUNNING));
    }

    private JobService service(JobRepository jobs, RunAttemptRepository attempts) {
        return new JobService(jobs, mock(ApplicationEventPublisher.class), mock(PipelineService.class),
                mock(AssetRepository.class), attempts, mock(OutboxService.class));
    }
}

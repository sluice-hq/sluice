package com.sluice.api.job.repository;

import com.sluice.api.job.domain.RunAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunAttemptRepository extends JpaRepository<RunAttempt, UUID> {
    Optional<RunAttempt> findByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);
    List<RunAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}

package com.sluice.api.step.repository;

import com.sluice.api.step.domain.StepRunAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepRunAttemptRepository extends JpaRepository<StepRunAttempt, UUID> {
    Optional<StepRunAttempt> findByStepRunIdAndAttemptNumber(UUID stepRunId, int attemptNumber);
    List<StepRunAttempt> findByStepRunIdOrderByAttemptNumberAsc(UUID stepRunId);
}

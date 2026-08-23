package com.sluice.api.step.repository;

import com.sluice.api.step.domain.StepRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StepRunRepository extends JpaRepository<StepRun, UUID> {
    List<StepRun> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}

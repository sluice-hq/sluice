package com.sluice.api.governance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GovernanceDecisionRepository extends JpaRepository<GovernanceDecision, UUID> {
    Optional<GovernanceDecision> findByJobIdAndStepRunId(UUID jobId, UUID stepRunId);
    List<GovernanceDecision> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}

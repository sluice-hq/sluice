package com.sluice.api.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GovernanceDecisionRepository extends JpaRepository<GovernanceDecision, UUID> {
    Optional<GovernanceDecision> findByJobIdAndStepRunId(UUID jobId, UUID stepRunId);
    @Query("select decision from GovernanceDecision decision where decision.jobId = :jobId order by decision.stepRun.stepIndex desc")
    List<GovernanceDecision> findLatestByJobId(@Param("jobId") UUID jobId, Pageable pageable);
}

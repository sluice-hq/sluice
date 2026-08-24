package com.sluice.api.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.repository.StepRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GovernanceDecisionService {
    private final GovernanceDecisionRepository decisions;
    private final StepRunRepository steps;
    private final ObjectMapper mapper;

    public GovernanceDecisionService(GovernanceDecisionRepository decisions, StepRunRepository steps,
                                     ObjectMapper mapper) {
        this.decisions = decisions; this.steps = steps; this.mapper = mapper;
    }

    @Transactional
    public GovernanceDecision persist(UUID jobId, String stepId, Map<String, Object> facts) {
        StepRun step = steps.findByJobIdAndStepId(jobId, stepId)
                .orElseThrow(() -> new IllegalStateException("Governance step is missing: " + stepId));
        return decisions.findByJobIdAndStepRunId(jobId, step.getId()).orElseGet(() -> decisions.save(
                new GovernanceDecision(UUID.randomUUID(), jobId, step.getId(),
                        String.valueOf(facts.getOrDefault("policyVersion", "1")),
                        String.valueOf(facts.get("provider")), nullable(facts.get("modelVersion")),
                        nullable(facts.get("providerRequestId")),
                        GovernanceDecisionValue.valueOf(String.valueOf(facts.get("governanceDecision"))),
                        mapper.valueToTree(facts.getOrDefault("categoryScores", Map.of())),
                        mapper.valueToTree(facts.getOrDefault("reasonCodes", java.util.List.of())))));
    }

    @Transactional(readOnly = true)
    public Optional<GovernanceDecision> latest(UUID jobId) {
        return decisions.findByJobIdOrderByCreatedAtAsc(jobId).stream().reduce((first, second) -> second);
    }

    private String nullable(Object value) { return value == null ? null : String.valueOf(value); }
}

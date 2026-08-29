package com.sluice.api.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.repository.StepRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceDecisionServiceTest {
    @Test
    void persistsNormalizedDecisionAgainstTheDurableStep() {
        UUID jobId = UUID.randomUUID();
        StepRun step = new StepRun(UUID.randomUUID(), jobId, "moderate", "governance.content-safety",
                "1.0.0", "COMPLETED");
        GovernanceDecisionRepository decisions = mock(GovernanceDecisionRepository.class);
        StepRunRepository steps = mock(StepRunRepository.class);
        when(steps.findByJobIdAndStepId(jobId, "moderate")).thenReturn(Optional.of(step));
        when(decisions.findByJobIdAndStepRunId(jobId, step.getId())).thenReturn(Optional.empty());
        when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new GovernanceDecisionService(decisions, steps, new ObjectMapper()).persist(jobId, "moderate", Map.of(
                "governanceDecision", "BLOCK", "policyVersion", "1", "provider", "test",
                "categoryScores", Map.of("violence", 7), "reasonCodes", List.of("violence_severity_7")));

        ArgumentCaptor<GovernanceDecision> saved = ArgumentCaptor.forClass(GovernanceDecision.class);
        verify(decisions).save(saved.capture());
        assertEquals(GovernanceDecisionValue.BLOCK, saved.getValue().getDecision());
        assertEquals(7, saved.getValue().getCategoryScores().path("violence").asInt());
        assertEquals(step.getId(), saved.getValue().getStepRunId());
    }

    @Test
    void returnsTheDecisionFromTheLastGovernanceStep() {
        UUID jobId = UUID.randomUUID();
        GovernanceDecision expected = mock(GovernanceDecision.class);
        GovernanceDecisionRepository decisions = mock(GovernanceDecisionRepository.class);
        when(decisions.findLatestByJobId(eq(jobId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(expected));

        Optional<GovernanceDecision> latest = new GovernanceDecisionService(
                decisions, mock(StepRunRepository.class), new ObjectMapper()).latest(jobId);

        assertEquals(expected, latest.orElseThrow());
    }
}

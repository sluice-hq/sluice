package com.sluice.api.step.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.domain.StepRunAttempt;
import com.sluice.api.step.repository.StepRunAttemptRepository;
import com.sluice.api.step.repository.StepRunRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepRunServiceTest {

    @Test
    void retainsTheFailedAttemptWhenTheNextRetryStarts() throws Exception {
        UUID jobId = UUID.randomUUID();
        StepRun step = new StepRun(UUID.randomUUID(), jobId, "resize", "resize", "1.0.0", "PENDING");
        StepRunRepository steps = mock(StepRunRepository.class);
        StepRunAttemptRepository attempts = mock(StepRunAttemptRepository.class);
        when(steps.findByJobIdAndStepId(jobId, "resize")).thenReturn(Optional.of(step));

        Map<Integer, StepRunAttempt> history = new HashMap<>();
        when(attempts.findByStepRunIdAndAttemptNumber(any(), any(Integer.class)))
                .thenAnswer(invocation -> Optional.ofNullable(history.get(invocation.getArgument(1))));
        when(attempts.save(any(StepRunAttempt.class))).thenAnswer(invocation -> {
            StepRunAttempt attempt = invocation.getArgument(0);
            history.put(attempt.getAttemptNumber(), attempt);
            return attempt;
        });
        MediaResource input = mock(MediaResource.class);
        when(input.getSize()).thenReturn(25L);
        StepRunService service = new StepRunService(steps, attempts, new ObjectMapper());

        service.start(jobId, "resize", 1, input);
        service.fail(jobId, "resize", "storage_unavailable", "retry safely");
        service.start(jobId, "resize", 2, input);

        assertEquals("FAILED", history.get(1).getStatus());
        assertEquals("storage_unavailable", history.get(1).getErrorCode());
        assertEquals("RUNNING", history.get(2).getStatus());
        assertEquals(2, step.getAttemptNumber());
        assertEquals("RUNNING", step.getStatus());
    }

    @Test
    void rejectsAttemptsBeyondTheWorkerRetryBound() {
        StepRunService service = new StepRunService(mock(StepRunRepository.class),
                mock(StepRunAttemptRepository.class), new ObjectMapper());

        assertThrows(IllegalArgumentException.class,
                () -> service.start(UUID.randomUUID(), "resize", 4, mock(MediaResource.class)));
    }
}

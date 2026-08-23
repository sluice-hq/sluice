package com.sluice.api.step.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.pipeline.MediaResource;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.domain.StepRunAttempt;
import com.sluice.api.step.repository.StepRunAttemptRepository;
import com.sluice.api.step.repository.StepRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class StepRunService {
    private static final int MAX_ATTEMPTS = 3;

    private final StepRunRepository steps;
    private final StepRunAttemptRepository attempts;
    private final ObjectMapper objectMapper;

    public StepRunService(StepRunRepository steps, StepRunAttemptRepository attempts, ObjectMapper objectMapper) {
        this.steps = steps;
        this.attempts = attempts;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void start(UUID jobId, String stepId, int attemptNumber, MediaResource input) {
        if (attemptNumber < 1 || attemptNumber > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Step attempt number must be between 1 and " + MAX_ATTEMPTS);
        }
        StepRun step = required(jobId, stepId);
        if (attempts.findByStepRunIdAndAttemptNumber(step.getId(), attemptNumber).isPresent()) {
            throw new IllegalStateException("Step attempt is already recorded: " + attemptNumber);
        }
        attempts.save(new StepRunAttempt(UUID.randomUUID(), step.getId(), attemptNumber,
                safeSize(input), contentType(input), java.time.Instant.now()));
        step.start(attemptNumber, safeSize(input), contentType(input));
        steps.save(step);
    }

    @Transactional
    public void complete(UUID jobId, String stepId, MediaResource output, Map<String, Object> metadata) {
        StepRun step = required(jobId, stepId);
        JsonNode facts = metadata == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(metadata);
        StepRunAttempt attempt = requiredAttempt(step);
        attempt.complete(safeSize(output), contentType(output), facts, java.time.Instant.now());
        attempts.save(attempt);
        step.complete(safeSize(output), contentType(output), facts);
        steps.save(step);
    }

    @Transactional
    public void fail(UUID jobId, String stepId, String errorCode, String safeMessage) {
        StepRun step = required(jobId, stepId);
        StepRunAttempt attempt = requiredAttempt(step);
        attempt.fail(errorCode, safeMessage, java.time.Instant.now());
        attempts.save(attempt);
        step.fail(errorCode, safeMessage);
        steps.save(step);
    }

    @Transactional
    public void attachOutput(UUID jobId, String stepId, UUID assetId) {
        StepRun step = required(jobId, stepId);
        step.attachOutput(assetId);
        steps.save(step);
    }

    private StepRun required(UUID jobId, String stepId) {
        return steps.findByJobIdAndStepId(jobId, stepId)
                .orElseThrow(() -> new IllegalStateException("Planned step is missing: " + stepId));
    }

    private StepRunAttempt requiredAttempt(StepRun step) {
        return attempts.findByStepRunIdAndAttemptNumber(step.getId(), step.getAttemptNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "Durable step attempt is missing: " + step.getAttemptNumber()));
    }

    private long safeSize(MediaResource resource) {
        try { return resource == null ? 0 : resource.getSize(); }
        catch (Exception ignored) { return 0; }
    }

    private String contentType(MediaResource resource) {
        return resource instanceof com.sluice.api.pipeline.FileMediaResource file ? file.getContentType() : null;
    }
}

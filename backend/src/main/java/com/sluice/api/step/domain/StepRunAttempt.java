package com.sluice.api.step.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_run_attempts", uniqueConstraints = @UniqueConstraint(
        name = "step_run_attempts_step_number_unique", columnNames = {"step_run_id", "attempt_number"}))
public class StepRunAttempt {
    @Id
    private UUID id;

    @Column(name = "step_run_id", nullable = false)
    private UUID stepRunId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_bytes")
    private Long inputBytes;

    @Column(name = "output_bytes")
    private Long outputBytes;

    @Column(name = "input_mime_type", length = 255)
    private String inputMimeType;

    @Column(name = "output_mime_type", length = 255)
    private String outputMimeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected StepRunAttempt() {
    }

    public StepRunAttempt(UUID id, UUID stepRunId, int attemptNumber, long inputBytes,
                          String inputMimeType, Instant startedAt) {
        this.id = id;
        this.stepRunId = stepRunId;
        this.attemptNumber = attemptNumber;
        this.status = "RUNNING";
        this.startedAt = startedAt;
        this.inputBytes = inputBytes;
        this.inputMimeType = inputMimeType;
        this.metadata = JsonNodeFactory.instance.objectNode();
    }

    public void complete(long outputBytes, String outputMimeType, JsonNode metadata, Instant completedAt) {
        requireRunning();
        this.status = "COMPLETED";
        this.completedAt = completedAt;
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.outputBytes = outputBytes;
        this.outputMimeType = outputMimeType;
        this.metadata = metadata;
    }

    public void fail(String code, String message, Instant completedAt) {
        requireRunning();
        this.status = "FAILED";
        this.completedAt = completedAt;
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.errorCode = code;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(500, message.length()));
    }

    private void requireRunning() {
        if (!"RUNNING".equals(status)) {
            throw new IllegalStateException("Step attempt is already terminal");
        }
    }

    public UUID getId() { return id; }
    public UUID getStepRunId() { return stepRunId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }
    public Long getInputBytes() { return inputBytes; }
    public Long getOutputBytes() { return outputBytes; }
    public String getInputMimeType() { return inputMimeType; }
    public String getOutputMimeType() { return outputMimeType; }
    public JsonNode getMetadata() { return metadata; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}

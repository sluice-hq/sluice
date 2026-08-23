package com.sluice.api.step.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "step_runs", uniqueConstraints = @UniqueConstraint(
        name = "step_runs_job_step_unique", columnNames = {"job_id", "step_id"}))
public class StepRun {
    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "step_id", nullable = false, length = 100)
    private String stepId;

    @Column(name = "processor_slug", nullable = false, length = 100)
    private String processorSlug;

    @Column(name = "processor_version", nullable = false, length = 32)
    private String processorVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at")
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

    @Column(name = "output_asset_id")
    private UUID outputAssetId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected StepRun() {}

    public StepRun(UUID id, UUID jobId, String stepId, String processorSlug, String processorVersion,
                   String status, int stepIndex, JsonNode metadata) {
        this.id = id;
        this.jobId = jobId;
        this.stepId = stepId;
        this.processorSlug = processorSlug;
        this.processorVersion = processorVersion;
        this.status = status;
        this.stepIndex = stepIndex;
        this.metadata = metadata;
    }

    public StepRun(UUID id, UUID jobId, String stepId, String processorSlug, String processorVersion, String status) {
        this(id, jobId, stepId, processorSlug, processorVersion, status, 0,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    }

    public void start(int attemptNumber, long inputBytes, String inputMimeType) {
        this.status = "RUNNING";
        this.attemptNumber = attemptNumber;
        this.startedAt = Instant.now();
        this.completedAt = null;
        this.durationMs = null;
        this.inputBytes = inputBytes;
        this.inputMimeType = inputMimeType;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void complete(long outputBytes, String outputMimeType, JsonNode metadata) {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.durationMs = startedAt == null ? null : java.time.Duration.between(startedAt, completedAt).toMillis();
        this.outputBytes = outputBytes;
        this.outputMimeType = outputMimeType;
        this.metadata = metadata;
    }

    public void fail(String code, String message) {
        this.status = "FAILED";
        this.completedAt = Instant.now();
        this.durationMs = startedAt == null ? null : java.time.Duration.between(startedAt, completedAt).toMillis();
        this.errorCode = code;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(500, message.length()));
    }

    public void attachOutput(UUID outputAssetId) { this.outputAssetId = outputAssetId; }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getStepId() { return stepId; }
    public String getProcessorSlug() { return processorSlug; }
    public String getProcessorVersion() { return processorVersion; }
    public String getStatus() { return status; }
    public int getStepIndex() { return stepIndex; }
    public int getAttemptNumber() { return attemptNumber; }
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
    public UUID getOutputAssetId() { return outputAssetId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
